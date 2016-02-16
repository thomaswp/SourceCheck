package com.snap.eval.policy;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

import astrecognition.model.Convert;
import astrecognition.model.Tree;
import pqgram.PQGram;
import pqgram.PQGramRecommendation;
import pqgram.Profile;
import pqgram.edits.Edit;
import pqgram.edits.Relabeling;

public class DirectEditPolicy implements HintPolicy {
	private final static int P = 2, Q = 3;
	
	public final Node target;
	
	public DirectEditPolicy(Node target) {
		this.target = target;
	}
	
	@Override
	public Set<Node> nextSteps(Node node) {
		return getEdits(node, target);
	}
	
	public static Set<Node> getEdits(Node from, Node to) {
		
		Tree fromTree = Convert.nodeToTree(from).makeLabelsUnique(new HashMap<>());
		Tree toTree = Convert.nodeToTree(to).makeLabelsUnique(new HashMap<>());
		
		HashMap<String, Tree> fromMap = new HashMap<>();
		addToMap(fromTree, fromMap);
		
		HashMap<String, Tree> toMap = new HashMap<>();
		addToMap(toTree, toMap);
		
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
		
		Set<Node> outcomes = new HashSet<>();
		for (Edit edit : edits) {
			Node outcome = edit.outcome(fromMap, toMap);
			if (outcome == null) {
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
	
	public static void main(String[] main) {
		testStudents();
	}

	@SuppressWarnings("unused")
	private static void testPath() {
		Node s = new Node(null, "A");
		s.addChild("B");
		s.addChild("C").addChild("Q").addChild("Y");
		
		
		Node t = new Node(null, "Z");
		t.addChild("Q");
		t.addChild("C").addChild("Y");
		
		testEdits(s, t);
	}
	
	private static void testStudents() {
		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree("../data/csc200/fall2015", "guess1Lab", maxTime, new HintFactoryMap());
				
		HashMap<String, List<Node>> students = subtree.nodeMap();
		for (String student : students.keySet()) {
			List<Node> nodes = students.get(student);
			Node last = nodes.get(nodes.size() - 1);
			
			int count = 0;
			for (Node node : nodes) {
				System.out.println("> " + node);
				Set<Node> edits = getEdits(node, last);
				for (Node edit : edits) {
					System.out.println("    " + node + " --> " + edit);
				}
				if (count++ > 20) break;
			}
			
			
			break;
		}
	}
	
	private static void testEdits(Node from, Node to) {
		
		Set<Node> edits = getEdits(from, to);
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

	@Override
	public Tuple<Node, Integer> solution(Node node, int maxSteps) {
		return new Tuple<Node, Integer>(target, -1);
	}
}
