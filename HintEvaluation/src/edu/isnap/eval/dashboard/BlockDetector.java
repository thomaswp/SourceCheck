package edu.isnap.eval.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

// A demo code for analyzing student snap code for each snapshot
public class BlockDetector {

	public static Assignment testData = Fall2016.Squiral;

	public static List<AssignmentAttempt> selectAttempts(Assignment assignment) {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			selected.add(attempt);
		}
		return selected;
	}

	public static void main(String[] args) {
		List<AssignmentAttempt> attempts = selectAttempts(testData);
		for (AssignmentAttempt attempt : attempts) {
			// for each project (submission)
			System.out.println(attempt.id);
			for (AttemptAction action : attempt) {
				if (action.snapshot == null) continue;
				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
				analyzeSyntaxTree(node);
				//System.out.println(node.prettyPrint(true));
			}
		}
	}

	public static void analyzeSyntaxTree(Node node)
	{
//		if(checkCustomBlock.pass(node)) {
//			System.out.println("Yes, this snapshot puts the majority of code in a custom block");
//		} else {
//			System.out.println("This snapshot failed the checkCustomBlock test");
//		}
		if(checkDoRepeat.pass(node)) {
			System.out.println("Yes, this snapshot uses a doRepeat block");
		} else {
			System.out.println("This user did not use a doRepeat block");
		}

	}

	public static class checkDoRepeat{
		private final static Predicate backbone = new Node.BackbonePredicate("doRepeat", "script");
		private final static Predicate usesDoRepeat = new Predicate() {
			@Override
			public boolean eval(Node node) {
				List<Node> doRepeats = node.searchAll(backbone);
				if (doRepeats.size() == 0) return false;
				else {
					//System.out.println("num blocks: " + doRepeats.size());
					return true;
				}
			}
		};
		private final static Predicate check =
				new Node.ConjunctionPredicate(true, backbone, usesDoRepeat);


		public static boolean pass(Node node) {
			return node.exists(check);


	}
	}

	public static class checkDoIfElse{
		private final static Predicate backbone = new Node.BackbonePredicate("down", "script");
		private final static Predicate usesDoIfElse = new Predicate() {
			@Override
			public boolean eval(Node node) {
				List<Node> ifElseBlocks = node.searchAll(backbone);
				System.out.println("num conditionals: " +ifElseBlocks.size());
				if (ifElseBlocks.size() == 0) return false;
				else {

					return true;
				}
			}

//			public int numOfCondition(Node node) {
//				List<Node> ifElseBlocks = node.searchAll(backbone);
//				System.out.println("num conditionals: " +ifElseBlocks.size());
//				return ifElseBlocks.size();
//			}
		};
		private final static Predicate check =
				new Node.ConjunctionPredicate(true, backbone, usesDoIfElse);


		public static boolean pass(Node node) {
			return node.exists(check);


	}
	}



//TODO: create a way for one class to be able to check multiple blocks
//	public static class checkBlocks{
//		private final static Predicate backbone1 = new Node.BackbonePredicate("doIfElse", "script");
//		private final static Predicate backbone2 = new Node.BackbonePredicate("doRepeat","script");
//		private static int item = 0;
//		private final static Predicate usesBlock = new Predicate() {
//			@Override
//			public boolean eval(Node node) {
//				List<Node> blockCheck;
//				blockCheck = node.searchAll(backbone1);
//
//
//
//				if (blockCheck.size() == 0) return false;
//				else {
//					System.out.println("num blocks: " + blockCheck.size());
//					return true;
//				}
//			}
//			public int numCheck(String blockName) {
//				if(blockName == "doIfElse") {
//					return 1;
//				} else {
//					return 2;
//				}
//			}
//		};
//
//		private final static Predicate check =
//				new Node.ConjunctionPredicate(true, backbone1, usesBlock);
//
//
//		public static boolean pass(Node node) {
//			return node.exists(check);
//
//
//	}
//	}

	public static class checkCustomBlock {

		private final static Predicate backbone =
				new Node.BackbonePredicate("customBlock", "script");

		private final static Predicate hasPrimaryCodeInCustomBlock = new Predicate() {
			@Override
			public boolean eval(Node node) {
				// if primary block exists in the script, return true.
				List<Node> customBlocks = node.searchAll(backbone);
				if (customBlocks.size() == 0) return false;


				List<Node> scripts =
						node.root().searchAll(new Node.BackbonePredicate("sprite", "script"));
				int maxScriptSize = 0;
				for (Node script : scripts) {
					if (script.treeSize() > maxScriptSize) {
						maxScriptSize = script.treeSize();
					}
				}
				for (Node customBlock : customBlocks) {
					if (customBlock.treeSize() > 2 && customBlock.treeSize() > maxScriptSize)
						return true;
				}
				return false;
			}
		};

		private final static Predicate check =
				new Node.ConjunctionPredicate(true, backbone, hasPrimaryCodeInCustomBlock);


		public static boolean pass(Node node) {
			return node.exists(check);
		}
	}

}
