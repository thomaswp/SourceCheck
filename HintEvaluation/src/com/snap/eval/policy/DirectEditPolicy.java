package com.snap.eval.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.snap.graph.data.Node;
import com.snap.graph.data.Tuple;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.parser.Assignment;

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
		return nextSteps(node, target);
	}
	
	public static Set<Node> nextSteps(Node from, Node to) {
		HashMap<String, Tree> fromMap = new HashMap<>();
		HashMap<String, Tree> toMap = new HashMap<>();
		
		List<Edit> edits = getEdits(from, to, fromMap, toMap);
		
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

	private static List<Edit> getEdits(Node from, Node to, HashMap<String, Tree> fromMap, HashMap<String, Tree> toMap) {
		Tree fromTree = Convert.nodeToTree(from).makeLabelsUnique(new HashMap<>());
		Tree toTree = Convert.nodeToTree(to).makeLabelsUnique(new HashMap<>());
		
		return getEdits(fromTree, toTree, fromMap, toMap);
	}

	private static List<Edit> getEdits(Tree fromTree, Tree toTree, HashMap<String, Tree> fromMap,
			HashMap<String, Tree> toMap) {
		addToMap(fromTree, fromMap);
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
		return edits;
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
		SnapSubtree subtree = new SnapSubtree(Assignment.Fall2015.GuessingGame1);
				
		Map<String, List<Node>> students = subtree.nodeMap();
		for (String student : students.keySet()) {
			List<Node> nodes = students.get(student);
			Node last = nodes.get(nodes.size() - 1);
			
			int count = 0;
			for (Node node : nodes) {
				System.out.println("> " + node);
				testEditsTopLevel(node, last);
//				Set<Node> edits = nextSteps(node, last);
//				for (Node edit : edits) {
//					System.out.println("    " + node + " --> " + edit);
//				}
				if (count++ > 20) break;
			}
			
			
			break;
		}
	}
	
	private static void pruneChildren(Tree fromTree, Tree toTree) {
		
		List<Tree> fromQueue = new ArrayList<>();
		fromQueue.add(fromTree);
		List<Tree> toQueue = new ArrayList<>();
		toQueue.add(toTree);
		
		while (!fromQueue.isEmpty()) {
			Tree f = fromQueue.remove(0);
			Tree t = toQueue.remove(0);

			boolean eq = false;
			if (f.getChildren().size() == t.getChildren().size()) {
				eq = true;
				for (int i = 0; i < f.getChildren().size(); i++) {
					if (!f.getChildren().get(i).getLabel().equals(t.getChildren().get(i).getLabel())) {
						eq = false;
						break;
					}
				}
			}
			fromQueue.addAll(f.getChildren());
			toQueue.addAll(t.getChildren());
			if (!eq) {
				break;
			}
		}
		
		for (Tree t : fromQueue) t.getChildren().clear();
		for (Tree t : toQueue) t.getChildren().clear();
	}
	
	private static void testEditsTopLevel(Node from, Node to) {
		if (from.equals(to)) return;
				
		HashMap<String, Tree> fromMap = new HashMap<>(), toMap = new HashMap<>();
		Tree fromTree = Convert.nodeToTree(from);
		Tree toTree = Convert.nodeToTree(to);
		pruneChildren(fromTree, toTree);
		fromTree.makeLabelsUnique(new HashMap<>());
		toTree.makeLabelsUnique(new HashMap<>());
//		String fromS = toString(fromTree);
//		String toS = toString(toTree);
		List<Edit> edits = getEdits(fromTree, toTree, fromMap, toMap);
		
		Node best = null;
		int bestDepth = Integer.MAX_VALUE;
		for (Edit edit : edits) {
			Node parent = edit.getParentNode(fromMap);
			if (parent == null) continue;
			int depth = parent.depth();
			if (depth < bestDepth) {
				Node outcome = edit.outcome(fromMap, toMap);
				if (outcome == null) continue;
				best = outcome;
				bestDepth = depth;
			}
		}
		
		if (best == null) {
			if (!from.equals(to)) { 
				System.err.println(from.prettyPrint() + "\n=!=\n" + to.prettyPrint());
			}
			return;
		}
		
//		System.out.println(fromS);
		testEditsTopLevel(best, to);
	}
	
	public static String toString(Tree tree) {
		String s = tree.getLabel();
		if (tree.getChildren().size() == 0) return s;
		s += "[";
		boolean space = false;
		for (Tree c : tree.getChildren()) {
			if (space) s += ", ";
			space = true;
			s += toString(c);
		}
		s += "]";
		return s;
	}

	private static void testEdits(Node from, Node to) {
		
		Set<Node> edits = nextSteps(from, to);
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
