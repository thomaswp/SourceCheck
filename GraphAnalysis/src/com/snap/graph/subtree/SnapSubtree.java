package com.snap.graph.subtree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.snap.graph.SimpleTreeBuilder;
import com.snap.parser.DataRow;
import com.snap.parser.SnapParser;

import util.LblTree;

public class SnapSubtree {
	public static void main(String[] args) throws IOException {
		SnapParser parser = new SnapParser("../data/csc200/fall2015", true);
		HashMap<String,List<DataRow>> students = parser.parseAssignment("guess1Lab");
		
		Builder builder = new Builder();
		for (String student : students.keySet()) {
			List<DataRow> rows = students.get(student);
			List<Node> nodes = new ArrayList<>();
			
			for (DataRow row : rows) {
				LblTree tree = SimpleTreeBuilder.toTree(row.snapshot, 0, true);
				Node node = Node.fromTree(null, tree);
				nodes.add(node);
			}
			
			builder.addStudent(nodes, false, false);
		}
	}
}
