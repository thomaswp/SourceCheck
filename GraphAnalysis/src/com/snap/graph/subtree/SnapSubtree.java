package com.snap.graph.subtree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.Node;
import com.snap.graph.data.Node.Action;
import com.snap.graph.data.VectorGraph;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.parser.DataRow;
import com.snap.parser.Grade;
import com.snap.parser.SnapParser;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store;
import com.snap.parser.Store.Mode;

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
import distance.RTED_InfoTree_Opt;
import util.LblTree;

public class SnapSubtree {

	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {

		System.out.println(System.currentTimeMillis());


		//		rtedTest();

		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab", maxTime, new HintFactoryMap());

//		subtree.outputStudentsFOG();

		// [0.0 - 1.0]
		double minGrade = 1;
		
		SubtreeBuilder builder = subtree.buildGraph(Mode.Overwrite, minGrade);

		//		subtree.getHints(builder, "0:{snapshot{stage{sprite{script{receiveGo}{doSetVar}{doSayFor}{doAsk}{doSayFor}{doSayFor}{abc}}}}{var}}");

		subtree.saveGraphs(builder, 1);

		//		subtree.printGoalMaps(builder);

		//		subtree.printFinalSolutions();

		//		subtree.printSomeHints(builder);

		//		subtree.analyze();

		System.out.println(System.currentTimeMillis());
	}

	//	private void printGoalMaps(SubtreeBuilder builder) {
	//		HintFactoryMap map = (HintFactoryMap) builder.hintMap;
	//		for (Node root : map.map.keySet()) {
	//			VectorGraph vectorGraph = map.map.get(root);
	//			
	//		}
	//	}

	@SuppressWarnings("unused")
	private void printSomeHints(SubtreeBuilder builder) {
		List<Node> student = nodeMap().values().iterator().next();
		for (int i = 0; i < student.size(); i++) {
			Node node = student.get(i);
			System.out.println("State: " + node);
			List<Hint> hints = builder.getHints(node);
			for (Hint hint : hints) {
				System.out.println(hint);
			}		
			if (i >= 5) break;
		}
	}

	public void getHints(SubtreeBuilder builder, String state) {
		Node node = Node.fromTree(null, LblTree.fromString(state), true);
		List<Hint> hints = builder.getHints(node);
		for (Hint hint : hints) {
			System.out.println(hint);
		}
	}

	public void printFinalSolutions() {
		HashMap<String,List<Node>> map = nodeMap();
		for (String student : map.keySet()) {
			List<Node> nodes = map.get(student);
			Node submitted = nodes.get(nodes.size() - 1);
			Snapshot snapshot = (Snapshot) submitted.tag;
			System.out.println(student);
			System.out.println(snapshot.toCode(true));
		}
	}

	@SuppressWarnings({ "unused", "unchecked" })
	private static void rtedTest() {
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(0.01, 0.01, 100);
		LblTree t1 = LblTree.fromString("0:{A{B{C}}{B{D{E}}}}");
		System.out.println(t1);
		LblTree t2 = LblTree.fromString("0:{A{B{C}{D{E}}}}");
		System.out.println(t2);
		opt.init(t1, t2);
		opt.computeOptimalStrategy();
		System.out.println(opt.nonNormalizedTreeDist());
		LinkedList<int[]> mapping = opt.computeEditMapping();
		ArrayList<LblTree> l1 = Collections.list(t1.depthFirstEnumeration());
		ArrayList<LblTree> l2 = Collections.list(t2.depthFirstEnumeration());
		for (int[] a : mapping) {
			LblTree c1 = a[0] == 0 ? null : l1.get(a[0] - 1);
			LblTree c2 = a[1] == 0 ? null : l2.get(a[1] - 1);
			System.out.println(c1 + " <==> " + c2);
		}
		System.exit(0);
	}

	public final String dataDir;
	public final String assignment;
	private final Date maxTime;
	private final HintMap hintMap;

	private HashMap<String, List<Node>> nodeMapCache;
	private HashMap<String, Grade> gradeMap;

	public void saveGraphs(SubtreeBuilder builder, int minVertices) throws FileNotFoundException {
		if (!(hintMap instanceof HintFactoryMap)) {
			System.out.println("No Hint Factory Map");
			return;
		}

		HashMap<Node, VectorGraph> map = ((HintFactoryMap) builder.hintMap).map;
		for (Node node : map.keySet()) {
			VectorGraph graph = map.get(node);
			if (graph.nVertices() < minVertices) continue;
			if (!graph.hasGoal()) continue;

			graph.bellmanBackup(2);
			String dir = String.format("%s/graphs/%s-g%03d/", dataDir, assignment, Math.round(builder.minGrade * 100));
			Node child = node;
			while (child.children.size() > 0) {
				dir += child.type() + "/";
				child = child.children.get(0);
			}
			new File(dir).mkdirs();
			File file = new File(dir, child.type());

			graph.export(new PrintStream(new FileOutputStream(file + ".graphml")), true, 0, false, true);
			graph.exportGoalContexts(new PrintStream(file + ".txt"));
		}
	}

	protected HashMap<String, List<Node>> nodeMap() {
		if (nodeMapCache == null) {
			try {
				parseStudents();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodeMapCache;
	}

	public SnapSubtree(String dataDir, String assignment, Date maxTime, HintMap hintMap) {
		this.dataDir = dataDir;
		this.assignment = assignment;
		this.maxTime = maxTime;
		this.hintMap = hintMap;
	}

	public SubtreeBuilder buildGraph(Mode storeMode) {
		return buildGraph(storeMode, 0);
	}
	
	public SubtreeBuilder buildGraph(Mode storeMode, final double minGrade) {
		String storePath = new File(dataDir, String.format("%s-g%03d.cached", assignment, Math.round(minGrade * 100))).getAbsolutePath();
		SubtreeBuilder builder = Store.getCachedObject(SubtreeBuilder.getKryo(), storePath, SubtreeBuilder.class, storeMode, new Store.Loader<SubtreeBuilder>() {
			@Override
			public SubtreeBuilder load() {
				return buildGraph((String)null, minGrade);
			}
		});
		return builder;
	}

	private final HashMap<String, HintMap> studentSubtreeCache = new HashMap<String, HintMap>();
	
	public SubtreeBuilder buildGraph(String testStudent, double minGrade) {
		final SubtreeBuilder builder = new SubtreeBuilder(hintMap.instance(), minGrade);
		builder.startBuilding();
		final AtomicInteger count = new AtomicInteger();
		for (String student : nodeMap().keySet()) {
			if (student.equals(testStudent)) continue;
			
			Grade grade = gradeMap.get(student);
			if (grade != null && grade.average() < minGrade) continue;
			
			final List<Node> nodes = nodeMap().get(student);
			if (nodes.size() == 0) continue;
			
			final String fStudent = student;
			
			count.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					HintMap studentMap;
					synchronized (studentSubtreeCache) {
						studentMap = studentSubtreeCache.get(fStudent);
					}
					if (studentMap == null) {
						studentMap = builder.addStudent(nodes);
						synchronized (studentSubtreeCache) {
							studentSubtreeCache.put(fStudent, studentMap);
						}
					} else {
						builder.addStudentMap(studentMap);
					}
					count.decrementAndGet();
				}
			}).start();
		}
		while (count.get() != 0) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		builder.finishedAdding();
		return builder;
	}

	@SuppressWarnings("unused")
	private void outputStudentsFOG() {
		int totalNodes = 0;
		String baseDir = dataDir + "/" + assignment + "/chf-fog/";
		new File(baseDir).mkdirs();

		HashMap<String,List<Node>> nodeMap = nodeMap();
		for (String student : nodeMap.keySet()) {
			Grade grade = gradeMap.get(student);
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
	private void outputStudents() {

		HashMap<String,List<Node>> nodeMap = nodeMap();
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

		String baseDir = dataDir + "/" + assignment + "/chf/";
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

	private void parseStudents() throws IOException {
		SnapParser parser = new SnapParser(dataDir, Store.Mode.Use);
		HashMap<String, SolutionPath> students = parser.parseAssignment(assignment);
		nodeMapCache = new HashMap<String, List<Node>>();
		gradeMap = new HashMap<String, Grade>();

		for (String student : students.keySet()) {
			SolutionPath path = students.get(student);
			if (!path.exported) continue;
			if (path.grade != null && path.grade.outlier) continue;
			List<Node> nodes = new ArrayList<Node>();

			for (DataRow row : path) {
				if (maxTime.before(row.timestamp)) {
//					System.out.println("Cutoff: " + student);
					break;
				}
				Node node = SimpleNodeBuilder.toTree(row.snapshot, true);
				nodes.add(node);
			}
			if (nodes.size() == 0) continue;
			if (path.grade == null) System.err.println("No grade for: " + student);
			nodeMapCache.put(student, nodes);
			gradeMap.put(student, path.grade);
		}
	}
}
