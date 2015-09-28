package com.snap.graph.subtree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import util.LblTree;

import com.snap.graph.SimpleTreeBuilder;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintComparator;
import com.snap.parser.DataRow;
import com.snap.parser.SnapParser;
import com.snap.parser.Store;
import com.snap.parser.Store.Mode;

public class SnapSubtree {
	
	public static void main(String[] args) throws IOException {
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab");
//		SubtreeBuilder graph = subtree.buildGraph(true);
		System.out.println(System.currentTimeMillis());
		subtree.buildGraph(Mode.Use);
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
	
	private void analyze() {
		float total = 0;
		int done = 0;
		for (String testKey : nodeMap().keySet()) {
			System.out.println(testKey);
			List<Node> test = nodeMap().get(testKey);
			SubtreeBuilder builder = buildGraph(testKey);
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
				return buildGraph((String)null);
			}
		});
	}
	
	public SubtreeBuilder buildGraph(String testStudent) {
		SubtreeBuilder builder = new SubtreeBuilder();
		List<Node> test = nodeMap().get(testStudent);
		for (List<Node> nodes : nodeMap().values()) {
			if (nodes == test) continue;
			builder.addStudent(nodes, true, false);
		}
		return builder;
	}

	private void parseStudents() throws IOException {
		SnapParser parser = new SnapParser(dataDir, Store.Mode.Use);
		HashMap<String,List<DataRow>> students = parser.parseAssignment(assignment);
		nodeMapCache = new HashMap<String, List<Node>>();
		
		for (String student : students.keySet()) {
			List<DataRow> rows = students.get(student);
			List<Node> nodes = new ArrayList<Node>();
			
			for (DataRow row : rows) {
				LblTree tree = SimpleTreeBuilder.toTree(row.snapshot, 0, true);
				Node node = Node.fromTree(null, tree, true);
				node.tag = row;
				nodes.add(node);
			}
			nodeMapCache.put(student, nodes);
		}
	}
}
