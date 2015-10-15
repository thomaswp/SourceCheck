package com.snap.graph.subtree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import util.LblTree;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;
import com.snap.graph.data.SkeletonMap;
import com.snap.graph.data.Node.Action;
import com.snap.graph.data.SimpleHintMap;
import com.snap.graph.subtree.SubtreeBuilder.WeightedHint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;
import com.snap.graph.subtree.SubtreeBuilder.HintComparator;
import com.snap.parser.DataRow;
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
import distance.RTED_InfoTree_Opt;

public class SnapSubtree {
	
	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		
//		rtedTest();
		
		
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab");
		
//		Class.forName("com.mysql.jdbc.Driver").newInstance();
//		Connection con = DriverManager.getConnection("jdbc:mysql://localhost/snap", "root", "Game1+1Learn!");
//		MySQLHintMap hintMap = new MySQLHintMap(con, "guess1Lab");
//		hintMap.clear();
		
		System.out.println(System.currentTimeMillis());
		subtree.buildGraph(Mode.Overwrite, false);
//		subtree.outputStudents();
//		subtree.analyze();
		System.out.println(System.currentTimeMillis());
	}

	@SuppressWarnings({ "unused", "unchecked" })
	private static void rtedTest() {
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(100, 100, 100);
		LblTree t1 = LblTree.fromString("0:{1{2{3}}}");
		System.out.println(t1);
		LblTree t2 = LblTree.fromString("1:{1{2{3{4}}{q}}}");
		System.out.println(t2);
		opt.init(t1, t2);
		opt.computeOptimalStrategy();
//		System.out.println(opt.nonNormalizedTreeDist());
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
	
	private HashMap<String, List<Node>> nodeMapCache;
	
	private HashMap<String, List<Node>> nodeMap() {
		if (nodeMapCache == null) {
			try {
				parseStudents();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodeMapCache;
	}
	
	public SnapSubtree(String dataDir, String assignment) {
		this.dataDir = dataDir;
		this.assignment = assignment;
	}
	
	@SuppressWarnings("unused")
	private void analyze() {
		float total = 0;
		int done = 0;
		for (String testKey : nodeMap().keySet()) {
			System.out.println(testKey);
			List<Node> test = nodeMap().get(testKey);
			SubtreeBuilder builder = buildGraph(new SubtreeBuilder(new SimpleHintMap()), testKey, null);
//			builder.graph.export(new PrintStream(new FileOutputStream("test" + done + ".graphml")), true, 1, true, true);

			for (Node node : test) {
				List<WeightedHint> hints = builder.getHints(node);
				Collections.sort(hints, HintComparator.ByContext.then(HintComparator.ByQuality));
				System.out.println(((Snapshot)node.tag).name);
				
				int context = Integer.MAX_VALUE;
				int printed = 0;
				for (int i = 0; i < hints.size() && printed < 5; i++) {
					WeightedHint hint = hints.get(i);
					if (hint.context == context) {
						continue;
					}
					context = hint.context;
					printed++;
					System.out.println(hints.get(i));
				}
				System.out.println("---------------------------");
			}
			
			done++;
			
			if (done == 1) break;
		}
		
		System.out.println(total / done);
	}

	
	public SubtreeBuilder buildGraph(Mode storeMode, final boolean write) {
		String storePath = new File(dataDir, assignment + ".cached").getAbsolutePath();
		return Store.getCachedObject(SubtreeBuilder.getKryo(), storePath, SubtreeBuilder.class, storeMode, new Store.Loader<SubtreeBuilder>() {
			@Override
			public SubtreeBuilder load() {
				if (write) {
					File out = new File(dataDir, "view/" + assignment + ".json.js");
					out.mkdirs();
					out.delete();
					PrintStream ps = null;
					try {
						ps = new PrintStream(out);
						return buildGraph(new SubtreeBuilder(new SimpleHintMap()), null, ps);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} finally {
						if (ps != null) ps.close();
					}
				}
				return buildGraph(new SubtreeBuilder(new SkeletonMap()), null, null);
			}
		});
	}
	
	public SubtreeBuilder buildGraph(SubtreeBuilder builder) {
		return buildGraph(builder, null, null);
	}
	
	public SubtreeBuilder buildGraph(final SubtreeBuilder builder, String testStudent, PrintStream out) {
		builder.startBuilding();
		List<Node> test = nodeMap().get(testStudent);
		jsonStart(out);
		final AtomicInteger count = new AtomicInteger();
		for (String student : nodeMap().keySet()) {
			final List<Node> nodes = nodeMap().get(student);
			final String fStudent = student;
			
			if (nodes == test || nodes.size() == 0) continue;
			count.incrementAndGet();
			if (out != null) {
				jsonStudent(out, student, nodes, builder.addStudent(nodes, false));
				if (out != null) break;
			}
			new Thread(new Runnable() {
				@Override
				public void run() {
					builder.addStudent(nodes, false);
					System.out.println("Added " + fStudent);
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
		jsonEnd(out);
		return builder;
	}
	
	private void jsonStart(PrintStream out) {
		if (out == null) return;
		out.println("var hintData = {");
	}
	
	private void jsonStudent(PrintStream out, String student, List<Node> nodes, List<List<HintChoice>> hints) {
		if (out == null) return;
		out.printf("\"%s\": [\n", student);
		for (int i = 1; i < nodes.size(); i++) {
			out.println("{");
			Node last = nodes.get(i - 1);
			out.printf("\"from\": \"%s\",\n", last);
			Node node = nodes.get(i);
			out.printf("\"to\": \"%s\",\n", node);
			out.println("\"hints\": [");
			
			for (HintChoice choice : hints.get(i)) {
				out.println(choice.toJson() + ",");
			}
			out.println("]},");
			
		}
		out.println("],");
	}
	
	private void jsonEnd(PrintStream out) {
		if (out == null) return;
		out.println("};");
	}
	
	private void outputStudents() {
		
		HashMap<String,List<Node>> nodeMap = nodeMap();
		final HashSet<String> labels = new HashSet<String>();
		for (List<Node> nodes : nodeMap.values()) {
			for (Node node : nodes) {
				node.recurse(new Action<Node>() {
					@Override
					public void run(Node item) {
						labels.add(item.type);
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
		n.setValue("label", new SymbolicValue(alpha, node.type));
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
		
		for (String student : students.keySet()) {
			SolutionPath path = students.get(student);
			if (!path.exported) continue;
			List<Node> nodes = new ArrayList<Node>();
			List<Node> submittedNodes = new ArrayList<Node>();
			
			for (DataRow row : path) {
				Node node = SimpleNodeBuilder.toTree(row.snapshot, true);
				nodes.add(node);
				
				if (row.action.equals("IDE.exportProject")) {
					submittedNodes.addAll(nodes);
					nodes.clear();
				}
			}
			nodeMapCache.put(student, submittedNodes);
		}
	}
}
