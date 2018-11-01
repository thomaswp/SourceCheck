package edu.isnap.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class PolygonAutoGrader {

	//	public static void main(String[] args) throws IOException {
	//		for (File xml : new File("tests").listFiles()) {
	//			if (!xml.getName().endsWith("polygon-solution4.xml"))
	//				continue;
	//			System.out.println(xml.getName() + ":");
	//
	//			Snapshot snapshot = Snapshot.parse(xml);
	//			Node node = SimpleNodeBuilder.toTree(snapshot, false);
	//			System.out.println(node); // print whole tree
	//
	//			for (Grader grader : PolygonGraders) {
	//				System.out.println(grader.name() + ": " + grader.pass(node));
	//			}
	//			System.out.println();
	//		}
	//	}
	public static void main(String[] args) throws IOException {
		for (File xml : new File("tests").listFiles()) {
			if (!xml.getName().endsWith("polygon-solution16.xml"))
				continue;
			System.out.println(xml.getName() + ":");

			Snapshot snapshot = Snapshot.parse(xml);
			Node node = SimpleNodeBuilder.toTree(snapshot, false);
			System.out.println(node); // print whole tree

			for (Grader grader : PolygonGraders) {
					System.out.println(grader.name() + ": " + grader.pass(node));
				}
					System.out.println();
		}
	}
//	public static void main(String[] args) throws IOException {
//
//		Assignment assignment = MTurk2018.PolygonMakerSimple;
//		//System.out.print(assignment);
//		analyzeSyntaxTree(assignment);
//
//	}

	public static void analyzeSyntaxTree(Assignment assignment) throws FileNotFoundException, IOException
	{
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Ignore, false, true);
//		int attemptCount = 0;
//		int snapshotCount = 0;
//		int i=0;
		for (AssignmentAttempt attempt: attempts.values())
		{
//			++attemptCount;
		 AttemptAction lastRow = attempt.rows.get(attempt.rows.size()-1);
		 if(lastRow.lastSnapshot!=null)
		 {
			 Node tree = SimpleNodeBuilder.toTree(lastRow.lastSnapshot, false);
			// if(i<8)
			 {
				System.out.println(tree);
				for (Grader grader : PolygonGraders) {
								System.out.println(grader.name() + ": " + grader.pass(tree));
						}
			 }
		 }
//		 i++;

		}

	}


	public final static Grader[] PolygonGraders = new Grader[] {
			new PolygonGraderAskAndUseAnswer(),
			new PolygonGraderDrawSomething(),
			new PolygonGraderMovesShape(),
			new PolygonGraderTurnCorrectly(),

	};
	// "Ask followed by a repeat on answer";
	public static class PolygonGraderAskAndUseAnswer implements Grader {

		@Override
		public String name() {
			return "Ask followed by a repeat on answer";
		}

		// step 1
		private final static Predicate backbone = new Node.BackbonePredicate("sprite", "script");

		private final static Predicate hasAskName = new Predicate() {
			@Override
			public boolean eval(Node node) {
				// if ask block exists in the script, return true.
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				if (ask < 0) return false;
				int repeatIndex = node.searchChildren(new Node.TypePredicate("doRepeat"));
				if (repeatIndex < ask) return false;
				Node repeat = node.children.get(repeatIndex);
				int answerIndex = repeat.searchChildren(new Node.TypePredicate("getLastAnswer"));
				return answerIndex == 0;
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasAskName);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	// "Draws something"
	public static class PolygonGraderDrawSomething implements Grader {

		@Override
		public String name() {
			return "Draws Something";
		}

		// step 1
		private final static Predicate backbone = new Node.BackbonePredicate("down");

		private final static Predicate Draws = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int downIndex = node.index();
				if(downIndex<0)
					return false;
				Node forwardNode = node.parent().search(new Node.BackbonePredicate("forward"));
				if (forwardNode == null) return false;

				if (forwardNode.parent() == node.parent()) {
					return forwardNode.index() > downIndex;
				}
				else return forwardNode.depth() > node.depth();
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, Draws);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	// "Move Shape"
	public static class PolygonGraderMovesShape implements Grader {
		@Override
		public String name() {
			return "Moves shape (repeat with turn and move)";
		}

		private final static Predicate backbone = new Node.BackbonePredicate("sprite|customBlock", "script", "...",
				"doRepeat");

		private final static Predicate MoveShape = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (node.children.size() < 2)
					return false;

				// check that it repeats on the answer.
				String t1 = node.children.get(0).type();
				if (!"getLastAnswer".equals(t1) && !"literal".equals(t1))
					return false;

				Node scriptNode = node.children.get(1); // get the script inside the repeat block

				// repeat block must have at least 2 blocks inside (move+turn).
				if (scriptNode.children.size() < 2)
					return false;
				else
				{ // turn and forward must exist in the repeat, if any doesn't
					// exist then return false
					if(scriptNode.searchChildren(new Node.TypePredicate("forward"))==-1
							&& (scriptNode.searchChildren(new Node.TypePredicate("turn"))==-1|| scriptNode.searchChildren(new Node.TypePredicate("turnLeft"))==-1))
						return false;
				}

				return true;
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, MoveShape);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	// "Turn Correctly"
	public static class PolygonGraderTurnCorrectly implements Grader {

		@Override
		public String name() {
			return "Turns Correctly";
		}

		// step 1
		private final static Predicate backbone = new Node.BackbonePredicate("turn|turnLeft");

		private final static Predicate Turns = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int turnIndex = node.index();
				if(turnIndex<0)
					return false;

			  // turn node must have only one child (the Quotient block)
					if (node.children.size() != 1)
						return false;

					String t2 = node.children.get(0).type();
					if (!"reportQuotient".equals(t2))
						return false;
					else {
						Node reportQuotientNode = node.children.get(0);

						if (reportQuotientNode.children.size() != 2)
							return false;

						String t3 = reportQuotientNode.children.get(0).value(); // here the value must be 360
						String t4 = reportQuotientNode.children.get(1).type(); // here the type must be "getLastAnswer"

						if (!"360".equals(t3))
							return false;
						if (!"getLastAnswer".equals(t4))
							return false;
					}
					return true; // all conditions are met!!
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, Turns);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}


}
