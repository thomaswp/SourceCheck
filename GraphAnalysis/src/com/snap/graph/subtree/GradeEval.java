package com.snap.graph.subtree;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

import com.snap.data.Snapshot;
import com.snap.graph.Alignment;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.graph.data.Node;
import com.snap.graph.data.Node.Predicate;
import com.snap.graph.subtree.AutoGrader.Grader;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class GradeEval {
	public static void main(String[] args) {
		
//		editTest();
//		if (true) return;

		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab", maxTime, new HintFactoryMap());
		
		HashMap<String, Tuple<Integer, Integer>> outcomes = new HashMap<String, Tuple<Integer, Integer>>();
		for (Grader grader : AutoGrader.graders) {
			outcomes.put(grader.name(), new Tuple<Integer, Integer>(0, 0));
		}
		
		int total = 0;
		
		Predicate ignoreHints = new Node.TypePredicate("stage", "sprite", "customBlock");
		
		HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			System.out.println(student);
			
			List<Node> nodes = nodeMap.get(student);		
			SubtreeBuilder builder = subtree.buildGraph(student, null);
			
			
			for (Node node : nodes) {
				HashMap<String,Boolean> grade = AutoGrader.grade(node);
				
				List<Hint> hints = builder.getHints(node);
				for (Hint hint : hints) {
					VectorHint vHint = (VectorHint) hint;
					if (ignoreHints.eval(vHint.root)) continue;
					
					Node next = hint.outcome().root();

					boolean p = false;
					
					HashMap<String,Boolean> nextGrade = AutoGrader.grade(next);
					for (String obj : nextGrade.keySet()) {
						boolean a = grade.get(obj);
						boolean b = nextGrade.get(obj);
						
						if (a == true && b == false) {
							outcomes.get(obj).x++;
							System.out.println("Regress: " + obj);
							p = true;
						} else if (a == false && b == true) {
							outcomes.get(obj).y++;
//							System.out.println("Complete: " + obj);
//							p = true;
						}
					}
					total++;

					if (p) {
//						System.out.println("Hint: " + hint.from() + " -> " + hint.to());
						System.out.println(((Snapshot)node.tag).name);
						System.out.println("From: " + node);
						System.out.println("To:   " + next);
						System.out.println();
					}
				}
			}
			
			for (String obj : outcomes.keySet()) {
				Tuple<Integer, Integer> outcome = outcomes.get(obj);
				System.out.println(obj + ": " + outcome);
			}
			
			System.out.println("Total: " + total);
			
			break;
		}
	}

	private static void editTest() {
		String[] seqA = new String[] { "A", "B",};
		String[] seqB = new String[] { "A", "B", "C", "B"};
		while (seqA != null) {
			System.out.println(Arrays.toString(seqA));
			seqA = Alignment.smartScriptEdit(seqA, seqB);
		}
	}
}
