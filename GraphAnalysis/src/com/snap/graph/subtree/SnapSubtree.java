package com.snap.graph.subtree;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import util.LblTree;

import com.snap.graph.SimpleTreeBuilder;
import com.snap.graph.data.Node;
import com.snap.graph.data.SimpleHintMap;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintComparator;
import com.snap.parser.DataRow;
import com.snap.parser.SnapParser;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store;
import com.snap.parser.Store.Mode;

public class SnapSubtree {
	
	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab");
		
//		Class.forName("com.mysql.jdbc.Driver").newInstance();
//		Connection con = DriverManager.getConnection("jdbc:mysql://localhost/snap", "root", "Game1+1Learn!");
//		MySQLHintMap hintMap = new MySQLHintMap(con, "guess1Lab");
//		hintMap.clear();
		
		System.out.println(System.currentTimeMillis());
		subtree.buildGraph(Mode.Overwrite);
//		subtree.analyze();
		System.out.println(System.currentTimeMillis());
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
			SubtreeBuilder builder = buildGraph(new SubtreeBuilder(new SimpleHintMap()), testKey);
//			builder.graph.export(new PrintStream(new FileOutputStream("test" + done + ".graphml")), true, 1, true, true);

			for (Node node : test) {
				List<Hint> hints = builder.getHints(node);
				Collections.sort(hints, HintComparator.ByContext.then(HintComparator.ByQuality));
				System.out.println(((DataRow)node.tag).timestamp);
				
				int context = Integer.MAX_VALUE;
				int printed = 0;
				for (int i = 0; i < hints.size() && printed < 5; i++) {
					Hint hint = hints.get(i);
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

	
	public SubtreeBuilder buildGraph(Mode storeMode) {
		String storePath = new File(dataDir, assignment + ".cached").getAbsolutePath();
		return Store.getCachedObject(SubtreeBuilder.getKryo(), storePath, SubtreeBuilder.class, storeMode, new Store.Loader<SubtreeBuilder>() {
			@Override
			public SubtreeBuilder load() {
				return buildGraph(new SubtreeBuilder(new SimpleHintMap()), null);
			}
		});
	}
	
	public SubtreeBuilder buildGraph(SubtreeBuilder builder, String testStudent) {
		builder.startBuilding();
		List<Node> test = nodeMap().get(testStudent);
		for (String student : nodeMap().keySet()) {
			List<Node> nodes = nodeMap().get(student);
			if (nodes == test) continue;
			System.out.println("Adding " + student);
			builder.addStudent(nodes, true, false);
		}
		return builder;
	}

	private void parseStudents() throws IOException {
		SnapParser parser = new SnapParser(dataDir, Store.Mode.Overwrite);
		HashMap<String, SolutionPath> students = parser.parseAssignment(assignment);
		nodeMapCache = new HashMap<String, List<Node>>();
		
		for (String student : students.keySet()) {
			SolutionPath path = students.get(student);
			if (!path.exported) continue;
			List<Node> nodes = new ArrayList<Node>();
			List<Node> submittedNodes = new ArrayList<Node>();
			
			for (DataRow row : path) {
				LblTree tree = SimpleTreeBuilder.toTree(row.snapshot, 0, true);
				Node node = Node.fromTree(null, tree, true);
				node.tag = row;
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
