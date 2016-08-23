package com.snap.graph.subtree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Hint;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.Node;
import com.snap.graph.data.VectorGraph;
import com.snap.parser.Assignment;
import com.snap.parser.DataRow;
import com.snap.parser.Grade;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store;
import com.snap.parser.Store.Mode;

import distance.RTED_InfoTree_Opt;
import util.LblTree;

public class SnapSubtree {

	public static void main(String[] args) throws IOException, InstantiationException,
			IllegalAccessException, ClassNotFoundException, SQLException {



		//		rtedTest();
		SnapSubtree subtree = new SnapSubtree(Assignment.Spring2016.PolygonMaker);
		subtree.nodeMap();

//		System.out.print("Go");
//		new Scanner(System.in).nextLine();

		System.out.println(System.currentTimeMillis());

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


	public final Assignment assignment;
	private final HintMap hintMap;

	private Map<String, List<Node>> nodeMapCache;
	private Map<String, Grade> gradeMapCache;

	public Map<String, List<Node>> nodeMap() {
		if (nodeMapCache == null) {
			try {
				parseStudents();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodeMapCache;
	}

	public Map<String, Grade> gradeMap() {
		if (gradeMapCache == null) {
			try {
				parseStudents();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return gradeMapCache;
	}

	public SnapSubtree(Assignment assignment) {
		this(assignment, new HintFactoryMap());
	}

	public SnapSubtree(Assignment assignment, HintMap hintMap) {
		this.assignment = assignment;
		this.hintMap = hintMap;
	}

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
		Map<String,List<Node>> map = nodeMap();
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

	public void saveGraphs(SubtreeBuilder builder, int minVertices)
			throws FileNotFoundException {
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
			String dir = String.format("%s/graphs/%s-g%03d/", assignment.dataDir,
					assignment.name, Math.round(builder.minGrade * 100));
			Node child = node;
			while (child.children.size() > 0) {
				dir += child.type() + "/";
				child = child.children.get(0);
			}
			new File(dir).mkdirs();
			File file = new File(dir, child.type());

			graph.export(new PrintStream(new FileOutputStream(file + ".graphml")), true,
					0, false, true);
			graph.exportGoalContexts(new PrintStream(file + ".txt"));
		}
	}

	public SubtreeBuilder buildGraph(Mode storeMode) {
		return buildGraph(storeMode, 0);
	}

	public SubtreeBuilder buildGraph(Mode storeMode, final double minGrade) {
		String storePath = new File(assignment.dataDir, String.format("%s-g%03d.cached",
				assignment.name, Math.round(minGrade * 100))).getAbsolutePath();
		SubtreeBuilder builder = Store.getCachedObject(SubtreeBuilder.getKryo(),
				storePath, SubtreeBuilder.class, storeMode,
				new Store.Loader<SubtreeBuilder>() {
			@Override
			public SubtreeBuilder load() {
				return buildGraph((String)null, minGrade);
			}
		});
		return builder;
	}

	private final HashMap<String, HintMap> studentSubtreeCache =
			new HashMap<String, HintMap>();

	public SubtreeBuilder buildGraph(String testStudent, double minGrade) {
		final SubtreeBuilder builder = new SubtreeBuilder(hintMap.instance(), minGrade);
		builder.startBuilding();
		final AtomicInteger count = new AtomicInteger();
		for (String student : nodeMap().keySet()) {
			if (student.equals(testStudent)) continue;

			Grade grade = gradeMapCache.get(student);
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
//						System.out.println(fStudent);
						studentMap = builder.addStudent(nodes, assignment.hasIDs);
						synchronized (studentSubtreeCache) {
							studentSubtreeCache.put(fStudent, studentMap);
						}
					} else {
						builder.addStudentMap(studentMap);
					}
					count.decrementAndGet();
				}
			}).run(); // Threading this causes bugs
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

	private void parseStudents() throws IOException {
		Map<String, SolutionPath> students = assignment.load(Mode.Use, true);
		nodeMapCache = new TreeMap<String, List<Node>>();
		gradeMapCache = new TreeMap<String, Grade>();

		for (String student : students.keySet()) {
			SolutionPath path = students.get(student);
			if (!path.exported) continue;
			List<Node> nodes = new ArrayList<Node>();

			for (DataRow row : path) {
				Node node = SimpleNodeBuilder.toTree(row.snapshot, true);
				nodes.add(node);
			}

			if (nodes.size() == 0) continue;
			if (assignment.graded && path.grade == null) {
				System.err.println("No grade for: " + student);
			}

			nodeMapCache.put(student, nodes);
			gradeMapCache.put(student, path.grade);
		}
	}
}
