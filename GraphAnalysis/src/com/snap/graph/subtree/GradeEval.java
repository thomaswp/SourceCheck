package com.snap.graph.subtree;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		
		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab", maxTime, new HintFactoryMap());
		
//		if (1 == 1) {
//			SubtreeBuilder b = subtree.buildGraph(Mode.Use, false);
//			LblTree tree = LblTree.fromString("{snapshot{stage{sprite{script{receiveGo}{doSayFor}}}}}");
//			List<Hint> hints = b.getHints(Node.fromTree(null, tree, true));
//			for (Hint hint : hints) {
//				System.out.println("From: " + hint.from());
//				System.out.println("To: " + hint.to());
//			}
//			return;
//		}
		
		HashMap<String, Tuple<Integer, Integer>> outcomes = new HashMap<String, Tuple<Integer, Integer>>();
		for (Grader grader : AutoGrader.graders) {
			outcomes.put(grader.name(), new Tuple<Integer, Integer>(0, 0));
		}
		
		int total = 0;
		
		// TODO: Don't forget you're not evaluating these hints
		Predicate ignoreHints = new Node.TypePredicate("stage", "sprite", "customBlock");
		
		double minGrade = 0;
		int skip = 1;
		
		HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (skip > 0) {
				skip--;
				continue;
			}
			
			System.out.println(student);
			
			List<Node> nodes = nodeMap.get(student);		
			SubtreeBuilder builder = subtree.buildGraph(student, minGrade);
			
			
			Set<Tuple<VectorHint, Boolean>> seen = new HashSet<Tuple<VectorHint, Boolean>>();
			
			for (Node node : nodes) {
				HashMap<String,Boolean> grade = AutoGrader.grade(node);
				
				List<Hint> hints = builder.getHints(node);
				for (Hint hint : hints) {
					VectorHint vHint = (VectorHint) hint;
					if (vHint.caution) continue;
					if (ignoreHints.eval(vHint.root)) continue;
					
					Node next = hint.outcome().root();

					boolean p = false;
					
					HashMap<String,Boolean> nextGrade = AutoGrader.grade(next);
					for (String obj : nextGrade.keySet()) {
						boolean a = grade.get(obj);
						boolean b = nextGrade.get(obj);
						
						if (a == true && b == false) {
							Tuple<VectorHint, Boolean> record = new Tuple<VectorHint, Boolean>(vHint, false);
							if (seen.add(record)) {
								outcomes.get(obj).x++;
//								System.out.println("Regress: " + obj);
//								p = true;
							}
						} else if (a == false && b == true) {
							Tuple<VectorHint, Boolean> record = new Tuple<VectorHint, Boolean>(vHint, true);
							if (seen.add(record)) {
								outcomes.get(obj).y++;
//								System.out.println("Complete: " + obj);
//								p = true;
							}
						}
					}
					total++;

					if (p) {
//						System.out.println("Hint: " + hint.from() + " -> " + hint.to());
						System.out.println(((Snapshot)node.tag).name);
						System.out.println("Code: " + ((Snapshot)node.tag).toCode());
						System.out.println("From: " + hint.from());
						System.out.println("To  : " + hint.to());
						System.out.println();
					}
				}
			}
			
//			break;
		}
		
		for (String obj : outcomes.keySet()) {
			Tuple<Integer, Integer> outcome = outcomes.get(obj);
			System.out.println(obj + ": " + outcome);
		}
		
		System.out.println("Total: " + total);
	}

	@SuppressWarnings("unused")
	private static void editTest() {
		String[] seqA = new String[] { "A", "B",};
		String[] seqB = new String[] { "A", "B", "C", "B"};
		while (seqA != null) {
			System.out.println(Arrays.toString(seqA));
			seqA = Alignment.smartScriptEdit(seqA, seqB);
		}
	}
}
