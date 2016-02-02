package com.snap.eval.grades;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.snap.graph.data.Node;

import astrecognition.model.Convert;
import astrecognition.model.Tree;
import pqgram.PQGram;
import pqgram.PQGramRecommendation;
import pqgram.Profile;
import pqgram.edits.Edit;
import pqgram.edits.Relabeling;

public class PQGramEdits {
	private final static int P = 2, Q = 3;
	
	public static void main(String[] main) {
		Node s = new Node(null, "A");
		s.addChild("B");
		s.addChild("C").addChild("Q");
		
		
		Node t = new Node(null, "Z");
		t.addChild("Q");
		t.addChild("C").addChild("Y");
		
		new PQGramEdits().testEdits(s, t);
	}
	
	private void testEdits(Node from, Node to) {
		
		List<Node> edits = getEdits(from, to);
		if (edits.size() == 0) {
			if (!from.equals(to)) { 
				System.err.println(from + " =!= " + to);
			}
			return;
		}

		System.out.println(from + " --> " + to);
		for (Node edit : edits) {
			System.out.println(" + " + edit);
		}
		
		for (Node edit : edits) {
			testEdits(edit, to);
		}
	}
	
	public List<Node> getEdits(Node from, Node to) {
		
		Tree fromTree = Convert.nodeToTree(from).makeLabelsUnique(new HashMap<>());
		Tree toTree = Convert.nodeToTree(to).makeLabelsUnique(new HashMap<>());
		
		HashMap<String, Tree> fromMap = new HashMap<>();
		addToMap(fromTree, fromMap);
		
//		HashMap<String, Tree> toMap = new HashMap<>();
//		addToMap(toTree, toMap);
		
		Profile fromProfile = PQGram.getProfile(fromTree, P, Q);
		Profile toProfile = PQGram.getProfile(toTree, P, Q);
		
		List<Edit> edits = PQGramRecommendation.getEdits(fromProfile, toProfile, fromTree, toTree);
		
		for (Edit edit : edits) {
			if (edit instanceof Relabeling) {
				Tree original = fromMap.get(edit.getA());
				String newLabel = edit.getB();
				if (original != null && !fromMap.containsKey(newLabel)) {
					fromMap.put(newLabel, original);
				}
			}
		}
		
		for (Edit edit : edits) {
			if (edit instanceof Relabeling) {
				Tree original = fromMap.get(edit.getA());
				String newLabel = edit.getB();
				if (original != null && !fromMap.containsKey(newLabel)) {
					fromMap.put(newLabel, original);
				}
			}
		}
		
		List<Node> outcomes = new LinkedList<>();
		for (Edit edit : edits) {
			Node outcome = edit.outcome(fromMap);
			if (outcome == null) {
				System.err.println(edit);
				continue;
			}
			outcomes.add(outcome);
		}
		
		return outcomes;
		
	}
	
	private static void addToMap(Tree tree, HashMap<String, Tree> map) {
		map.put(tree.getUniqueLabel(), tree);
		for (Tree child : tree.getChildren()) {
			addToMap(child, map);
		}
	}
}
