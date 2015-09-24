package com.snap.graph.subtree;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import util.LblTree;

import com.snap.graph.SimpleTreeBuilder;
import com.snap.parser.DataRow;
import com.snap.parser.SnapParser;

public class SnapSubtree {
	public static void main(String[] args) throws IOException {
		SnapParser parser = new SnapParser("../data/csc200/fall2015", true);
		HashMap<String,List<DataRow>> students = parser.parseAssignment("guess1Lab");
		HashMap<String, List<Node>> nodeMap = new HashMap<String, List<Node>>();
		
		for (String student : students.keySet()) {
			List<DataRow> rows = students.get(student);
			List<Node> nodes = new ArrayList<>();
			
			for (DataRow row : rows) {
				LblTree tree = SimpleTreeBuilder.toTree(row.snapshot, 0, true);
				Node node = Node.fromTree(null, tree);
				nodes.add(node);
			}
			nodeMap.put(student, nodes);
		}

		float total = 0;
		int done = 0;
		boolean subtree = true;
		for (String testKey : nodeMap.keySet()) {
			System.out.println(testKey);
			List<Node> test = nodeMap.get(testKey);
			Builder builder = new Builder();
			for (List<Node> nodes : nodeMap.values()) {
				if (nodes == test) continue;
				builder.addStudent(nodes, subtree, false);
			}
			builder.graph.export(new PrintStream(new FileOutputStream("test" + done + ".graphml")), true, 1, true, true);
			total += builder.testStudent(test, subtree);
			
			done++;
			
			if (done == 1) break;
		}
		
		System.out.println(total / done);
	}
}
