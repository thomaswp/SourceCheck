package pqgram;

import java.util.HashMap;
import java.util.List;

import astrecognition.model.Tree;
import pqgram.edits.Edit;

public class Test {

	public static void main(String[] args) {
		HashMap<String, Integer> map = new HashMap<>();
		
		Tree tree1 = new Tree("A");
		tree1.addChild(new Tree(tree1, "B"));
		tree1.addChild(new Tree(tree1, "C"));
		
		Tree tree2 = new Tree("Q");
		tree2.addChild(new Tree(tree2, "X"));
		Tree child = new Tree(tree2, "Q");
		child.addChild(new Tree(child, "C"));
		tree2.addChild(child);
		
		tree1.makeLabelsUnique(map);
		tree2.makeLabelsUnique(map);
		
		System.out.println(toString(tree1));
		System.out.println(toString(tree2));
		
		int p = 3, q = 2;
		
		Profile p1 = PQGram.getProfile(tree1, p, q);
		Profile p2 = PQGram.getProfile(tree2, p, q);
		List<Edit> edits = PQGramRecommendation.getEdits(p1, p2, tree1, tree2);
		
		System.out.println("Edits:");
		for (Edit edit : edits) {
			System.out.println(edit);
		}
	}
	
	public static String toString(Tree t) {
		String s = t.toString();
		if (!t.isLeaf()) {
			s += ": [";
			boolean first = true;
			for (Tree child : t.getChildren()) {
				if (!first) s += ", ";
				first = false;
				s += toString(child);
			}
			s += "]";
		}
		return s;
	}
}
