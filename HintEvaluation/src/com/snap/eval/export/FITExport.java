package com.snap.eval.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.snap.data.Snapshot;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.data.Node.Action;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.parser.Grade;

import de.citec.tcs.alignment.csv.CSVExporter;
import de.citec.tcs.alignment.sequence.Alphabet;
import de.citec.tcs.alignment.sequence.KeywordSpecification;
import de.citec.tcs.alignment.sequence.NodeSpecification;
import de.citec.tcs.alignment.sequence.Sequence;
import de.citec.tcs.alignment.sequence.SymbolicKeywordSpecification;
import de.citec.tcs.alignment.sequence.SymbolicValue;
import de.unibi.citec.fit.objectgraphs.Graph;
import de.unibi.citec.fit.objectgraphs.api.factories.TreeFactory;
import de.unibi.citec.fit.objectgraphs.api.matlab.print.PlainTextPrintModule;

public class FITExport {
	
	public static void main(String[] args) {
		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab", maxTime, new HintFactoryMap());
		outputStudentsFOG(subtree);
	}
	
	private static void outputStudentsFOG(SnapSubtree subtree) {
		int totalNodes = 0;
		String baseDir = subtree.dataDir + "/" + subtree.assignment + "/chf-fog/";
		new File(baseDir).mkdirs();

		Map<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			Grade grade = subtree.gradeMap().get(student);
			totalNodes++;
			List<Node> nodes = nodeMap.get(student);
			String dir  = baseDir + student + "/";
			new File(dir).mkdirs();
			for (Node node : nodes) {
				// Let's transform that to the .fog format by transforming it to a
				// Graph object. For that I have some API classes provided that make
				// life easier. In this case we need a TreeFactory
				final TreeFactory factory = new TreeFactory();
				final Graph convertedTree = factory.createGraph();
				if (grade != null) factory.addMetaInformation(convertedTree, "grade", grade.average());
				// convert the tree recursively
				transform(node, convertedTree, null, factory);
				// and then we can serialize it to a .fog format string
				final PlainTextPrintModule print = new PlainTextPrintModule();
				String name = ((Snapshot)node.tag).name;
				try {
					FileOutputStream fos = new FileOutputStream(dir + name + ".fog");
					print.printGraph(convertedTree, fos);
					fos.close();
					totalNodes++;
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		}
		
		System.out.println("Total students: " + nodeMap.size());
		System.out.println("Total nodes: " + totalNodes);
	}

	private static void transform(Node node, Graph convertedTree, de.unibi.citec.fit.objectgraphs.Node convertedParent, TreeFactory factory) {
		final de.unibi.citec.fit.objectgraphs.Node convertedNode;
		if (convertedParent == null) {
			convertedNode = factory.createNode(convertedTree);
		} else {
			convertedNode = factory.createChild(convertedParent);
		}
		factory.addMetaInformation(convertedNode, "label", node.type());
		for (final Node child : node.children) {
			transform(child, convertedTree, convertedNode, factory);
		}
	}

	@SuppressWarnings("unused")
	private static void outputStudents(SnapSubtree subtree) {

		Map<String,List<Node>> nodeMap = subtree.nodeMap();
		final HashSet<String> labels = new HashSet<String>();
		for (List<Node> nodes : nodeMap.values()) {
			for (Node node : nodes) {
				node.recurse(new Action() {
					@Override
					public void run(Node item) {
						labels.add(item.type());
					}
				});
			}
		}

		String baseDir = subtree.dataDir + "/" + subtree.assignment + "/chf/";
		new File(baseDir).mkdirs();

		// this we want to transform to a Sequence in my format. For that we
		// need to specify the attributes of our nodes in the Sequence first.
		// our nodes have only one attribute, namely the label.
		// In my toolbox, there are three different kinds of attributes for
		// nodes, symbolic data, vectorial data and string data. A label in our
		// case is probably symbolic, meaning: There is only a finite set of
		// different possible labels. Symbolic, in that sense, is something like
		// an enum.
		// the alphabet specifies the different possible values.
		final Alphabet alpha = new Alphabet(labels.toArray(new String[labels.size()]));
		// the KeywordSpecification specifies the attribute overall.
		final SymbolicKeywordSpecification labelAttribute
		= new SymbolicKeywordSpecification(alpha, "label");
		// the NodeSpecification specifies all attributes of a node.
		final NodeSpecification nodeSpec = new NodeSpecification(
				new KeywordSpecification[]{labelAttribute});
		// and we can write that NodeSpecification to a JSON file.

		try {
			CSVExporter.exportNodeSpecification(nodeSpec, baseDir + "nodeSpec.json");
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		for (String student : nodeMap.keySet()) {
			List<Node> nodes = nodeMap.get(student);
			String dir  = baseDir + student + "/";
			new File(dir).mkdirs();
			for (Node node : nodes) {
				final Sequence seq = new Sequence(nodeSpec);
				appendNode(seq, alpha, node);
				String name = ((Snapshot)node.tag).name;
				// show it for fun
				//				System.out.println(name + ": " + seq.toString());
				// and then we can write it to a file.
				try {
					CSVExporter.exportSequence(seq, dir + name + ".csv");
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		}		
	}

	private static void appendNode(Sequence seq, Alphabet alpha, Node node) {
		// create a node for the sequence.
		final de.citec.tcs.alignment.sequence.Node n = new de.citec.tcs.alignment.sequence.Node(seq);
		// set its label
		n.setValue("label", new SymbolicValue(alpha, node.type()));
		// add it to the sequence.
		seq.getNodes().add(n);
		// recurse to the children.
		for (final Node child : node.children) {
			appendNode(seq, alpha, child);
		}
	}

}
