package pqgram;

import java.util.HashMap;
import java.util.List;

import astrecognition.model.Tree;
import pqgram.edits.Edit;

public class Test {

	public static void main(String[] args) {
		HashMap<String, Integer> map = new HashMap<>();

		Tree tree1 = new Tree("A");
		tree1.addChild(new Tree("X"));

		Tree tree2 = new Tree("A");
		Tree child1 = new Tree("X");
		tree2.addChild(child1);
		Tree child2 = new Tree("C");
		tree2.addChild(child2);

		child2.addChild(new Tree("A"));


		tree1.makeLabelsUnique(map);
		tree2.makeLabelsUnique(map);

		System.out.println(toString(tree1));
		System.out.println(toString(tree2));
		System.out.println();

		int p = 2, q = 3;

		Profile p1 = PQGram.getProfile(tree1, p, q);
		Profile p2 = PQGram.getProfile(tree2, p, q);

		System.out.println(p1);
		System.out.println(p2);

		List<Edit> edits = PQGramRecommendation.getEdits(p1, p2, tree1, tree2);

		System.out.println("Edits:");
		for (Edit edit : edits) {
			System.out.println(edit);
		}
	}

	public static String toString(Tree t) {
		String s = t.toString();
		if (!t.isLeaf()) {
			s += ":[";
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
