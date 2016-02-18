package com.snap.eval;

import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.snap.eval.util.Prune;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;

public class StudentEval {
	
	public final static int SKIP = 1, MAX = 100;
	
	public static void main(String[] args) throws IOException {
		String dir = "../data/csc200/fall2015";
		String assignment = "guess1Lab";
		eval(dir, assignment);
	}
	
	private static void eval(String dir, String assignment) throws IOException {

		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree(dir, assignment, maxTime, new HintFactoryMap());

		HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
		
		for (int i = 0; i < 2; i++) {
			int skip = SKIP;
			int max = MAX;
				
			HashSet<String> seen = new HashSet<>();
			HashSet<String> solutions = new HashSet<>();
			int totalNoDouble = 0;
			int total = 0;
			int students = 0;
			
			for (String student : nodeMap.keySet()) {
				if (skip-- > 0) {
					continue;
				}
	
				if (--max < 0) break;
				
				List<Node> nodes = nodeMap.get(student);
				if (i == 1) nodes = Prune.removeSmallerScripts(nodes);
				HashSet<String> studentSet = new HashSet<>();
				
				for (Node node : nodes) {
					String ns = node.toCanonicalString();
					seen.add(ns);
					studentSet.add(ns);
				}
	
				total += nodes.size();
				totalNoDouble += studentSet.size();
				solutions.add(nodes.get(nodes.size() - 1).toCanonicalString());
				students++;
			}
			
			System.out.println("Unique: " + seen.size() + "/" + total);
			System.out.println("Unique no double: " + seen.size()  + "/" +  totalNoDouble);
			System.out.println("Unique solutions: " + solutions.size()  + "/" +  students);
		}
	}
}
