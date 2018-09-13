package edu.isnap.eval;

import java.io.File;
import java.io.IOException;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;

public class TriangleSeriesAutoGrader {

	public static void main(String[] args) throws IOException {
		for (File xml : new File("tests").listFiles()) {
			if (!xml.getName().endsWith("triangle3.xml"))
				continue;
			System.out.println(xml.getName() + ":");

			Snapshot snapshot = Snapshot.parse(xml);
			Node node = SimpleNodeBuilder.toTree(snapshot, false);
			System.out.println(node); // print whole tree

			for (Grader grader : PolygonTriangleSeriesGraders) {
				System.out.println(grader.name() + ": " + grader.pass(node));
			}
			System.out.println();
		}
	}

	public final static Grader[] PolygonTriangleSeriesGraders = new Grader[] { new TriangleGraderAskAndUseAnswer(),
			new TriangleGraderDrawSomething(), new TriangleGraderMovestriangle(), new TriangleSeriesDrawTriangles(),

	};

// "Ask followed by a repeat on answer";
	public static class TriangleGraderAskAndUseAnswer implements Grader {

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
				if (ask < 0)
					return false;
				int repeatIndex = node.searchChildren(new Node.TypePredicate("doRepeat"));
				if (repeatIndex < ask)
					return false;
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
	public static class TriangleGraderDrawSomething implements Grader {

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
				if (downIndex < 0)
					return false;
				Node forwardNode = node.parent().search(new Node.BackbonePredicate("forward"));
				if (forwardNode == null)
					return false;

				if (forwardNode.parent() == node.parent()) {
					return forwardNode.index() > downIndex;
				} else
					return forwardNode.depth() > node.depth();
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, Draws);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	// "Move Triangle, whether in a repeat or iteratively"
	public static class TriangleGraderMovestriangle implements Grader {
		@Override
		public String name() {
			return "Moves triangle";
		}

		private static boolean checkTurnLiterals(Node nestedTurnNode) {
			String t3 = nestedTurnNode.children.get(0).value();
			String t4 = nestedTurnNode.children.get(0).type();
			if ("120".equals(t3))
				return true;
			else if ("reportQuotient".equals(t4)) {
				Node reportQuotientNode = nestedTurnNode.children.get(0);
				String t5 = reportQuotientNode.children.get(0).value(); // here the value must be 360
				String t6 = reportQuotientNode.children.get(1).value();
				if ("360".equals(t5) && "3".equals(t6))
					return true;
				else
					return false;

			} else
				return false;
		}

		private final static Predicate backboneRepeat = new Node.BackbonePredicate("doRepeat");

		private final static Predicate backboneForward = new Node.BackbonePredicate("forward");

		private final static Predicate MoveTriangleRepeat = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (node.index() < 0)
					return false;

				if (node.children.size() < 2)
					return false;

				String t1 = node.children.get(0).value();
				if (!"3".equals(t1))
					return false;

				Node scriptNode = node.children.get(1); // get the script inside the repeat block

				// repeat block must have at least 2 blocks inside (move+turn).
				if (scriptNode.children.size() < 2)
					return false;
				else { // turn and forward must exist in the repeat, if any doesn't
						// exist then return false
					if (scriptNode.searchChildren(new Node.TypePredicate("forward")) == -1
							|| (scriptNode.searchChildren(new Node.TypePredicate("turn")) == -1
									&& scriptNode.searchChildren(new Node.TypePredicate("turnLeft")) == -1))
						return false;
				}
				int index3 = scriptNode.searchChildren(new Node.TypePredicate("turn"));
				if (index3 > -1) {
					Node nodeTurn = scriptNode.children.get(index3);
					return checkTurnLiterals(nodeTurn);
				} else {
					index3 = scriptNode.searchChildren(new Node.TypePredicate("turnLeft"));
					Node nodeTurn = scriptNode.children.get(index3);
					return checkTurnLiterals(nodeTurn);
				}

			}
		};
		private final static Predicate MoveTriangleIteratively = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (node.index() < 0)
					return false;

				// Node script = node.parent();
				int index1 = node.index();
				if (node.parent.children.size() - index1 < 6)
					return false;

				for (int i = index1 + 1; i < (index1 + 5); i += 2) {
					if (i == (node.parent().children().size() - 1)) // since it might have more than 6 children, but
																	// forward starts in the middle and no 5 blocks
																	// exists after it
						return false;
					Node next = node.parent().children().get(i);
					if ((next.type().equals("turn")) || (next.type().equals("turnLeft"))) {
						if (!checkTurnLiterals(next))
							return false;
					} else {
						return false;
					}
					Node next2 = node.parent().children().get(i + 1);
					if (!(next2.type().equals("forward")))
						return false;

				}

				return true;
			}

		};

		private final static Predicate testRepeat = new Node.ConjunctionPredicate(true, backboneRepeat,
				MoveTriangleRepeat);
		private final static Predicate testIterative = new Node.ConjunctionPredicate(true, backboneForward,
				MoveTriangleIteratively);

		private final static Predicate test = new Node.ConjunctionPredicate(false, testRepeat, testIterative);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	// Move series of Triangles.
	public static class TriangleSeriesDrawTriangles implements Grader {

		@Override
		public String name() {
			return "Draw Triangles";
		}

		// step 1
		private final static Predicate backbone = new Node.BackbonePredicate("sprite", "...", "doRepeat");

		private static boolean checkTurnLiterals(Node nestedTurnNode) {
			String t3 = nestedTurnNode.children.get(0).value();
			String t4 = nestedTurnNode.children.get(0).type();
			if ("120".equals(t3))
				return true;
			else if ("reportQuotient".equals(t4)) {
				Node reportQuotientNode = nestedTurnNode.children.get(0);
				String t5 = reportQuotientNode.children.get(0).value(); // here the value must be 360
				String t6 = reportQuotientNode.children.get(1).value();
				if ("360".equals(t5) && "3".equals(t6))
					return true;
				else
					return false;

			} else
				return false;
		}

		// check for the extra forward after drawing 1 triangle
		private static boolean checkExtraForward(Node scriptNodeFirstRepeat) {
			if (scriptNodeFirstRepeat.children.size() > 6) // check for the extra forward
			{
				// possibilities!!
				int indexForward = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("forward"), 6);
				int indexUp = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("up"), 6);
				// int indexDown = scriptNodeFirstRepeat.searchChildren(new
				// Node.TypePredicate("down"),6);
				if (indexForward == -1)
					return false;
				else if (indexUp > -1) // if there exist up, it must be before forward
				{
					if (indexForward > indexUp)
						return true;
					else
						return false;
				} else
					return true;
			} else
				return false;
		}

		private static boolean checkNestedRepeat(Node nestedRepeat) {
			if (nestedRepeat.children.size() < 2)
				return false;

			String t1 = nestedRepeat.children.get(0).value();
			if (!"3".equals(t1))
				return false;

			Node scriptNode = nestedRepeat.children.get(1); // get the script inside the repeat block

			// repeat block must have at least 2 blocks inside (move+turn).
			if (scriptNode.children.size() < 2)
				return false;
			else { // turn and forward must exist in the repeat, if any doesn't
					// exist then return false
				if (scriptNode.searchChildren(new Node.TypePredicate("forward")) == -1
						&& (scriptNode.searchChildren(new Node.TypePredicate("turn")) == -1
								|| scriptNode.searchChildren(new Node.TypePredicate("turnLeft")) == -1))
					return false;
			}
			int index3 = scriptNode.searchChildren(new Node.TypePredicate("turn"));
			if (index3 > -1) {
				Node nodeTurn = scriptNode.children.get(index3);
				return checkTurnLiterals(nodeTurn);
			} else {
				index3 = scriptNode.searchChildren(new Node.TypePredicate("turnLeft"));
				Node nodeTurn = scriptNode.children.get(index3);
				return checkTurnLiterals(nodeTurn);
			}
		}

		// check if the student draws a triangle iteratively not inside a repeat
		private static boolean checkRepeatIteratively(Node nestedRepeatNode) {
			// move, turn, move, turn, move, turn
			if (nestedRepeatNode.children.size() < 6)
				return false;
			else {
				int indexForward = nestedRepeatNode.searchChildren(new Node.TypePredicate("forward"), 0);
				int indexTurn = nestedRepeatNode.searchChildren(new Node.TypePredicate("turn"), 0);
				int indexTurnLeft = nestedRepeatNode.searchChildren(new Node.TypePredicate("turnLeft"), 0);
				if (indexForward > -1 && (indexTurn > -1 || indexTurnLeft > -1)) {
					int startIndex = 0;
					// it works for special conditions, need to think about in a better way!!
					if (indexTurn > indexForward || indexTurnLeft > indexForward)
						startIndex = indexForward;
					else if (indexTurn == -1)
						startIndex = indexTurnLeft;
					else
						startIndex = indexTurn;
					if (startIndex + 6 > nestedRepeatNode.children.size())
						return false;
					for (int i = startIndex; i < (startIndex + 6); i += 2) {

						String t1 = nestedRepeatNode.children.get(i).type();
						String t2 = nestedRepeatNode.children.get(i + 1).type();

						if ("forward".equals(t1) && ("turn".equals(t2) || "turnLeft".equals(t2))) {
							// send the turn node to checkRepeatNumber
							boolean check = checkTurnLiterals(nestedRepeatNode.children.get(i + 1));
							if (check == false)
								return false;

						} else if ("forward".equals(t2) && ("turn".equals(t1) || "turnLeft".equals(t1))) {
							boolean check = checkTurnLiterals(nestedRepeatNode.children.get(i));
							if (check == false)
								return false;
						} else
							return false;
					}
				} else
					return false;

			}
			return true;
		}

		private final static Predicate DrawTriangles = new Predicate() {
			@Override
			public boolean eval(Node node) {
				// repeat block must have at least 3 blocks inside (the answer + script).
				if (node.children.size() < 2)
					return false;

				// check that it repeats on the answer.
				String t1 = node.children.get(0).type();
				if (!"getLastAnswer".equals(t1) && !"literal".equals(t1))
					return false;

				Node scriptNodeFirstRepeat = node.children.get(1); // get the script inside the repeat block

				// repeat block must have at least 2 blocks inside (repeat + forward),
				// or it can have several move and turn.
				if (scriptNodeFirstRepeat.children.size() < 2)
					return false;
				else {
					// this mean it doesn't have a repeat , so check if it has iterative loop.
					if (scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("doRepeat")) == -1) {
						boolean iterativeRepeats = checkRepeatIteratively(scriptNodeFirstRepeat);
						if (iterativeRepeats) // if it has an iterative repeat, check the extra forward
						{
							return checkExtraForward(scriptNodeFirstRepeat);
//							
						}
					} // there exist a nested repeatblock, and then there must be forward block
					else if (scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("doRepeat")) > -1
							&& scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("forward")) > -1) {
						int index4 = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("doRepeat"));
						Node nestedRepeatNode = scriptNodeFirstRepeat.children.get(index4); // get the nested repeat
																							// block
						return checkNestedRepeat(nestedRepeatNode); // check if the nested loop is correct or not

					} else
						return false;

				}
				return false;
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, DrawTriangles);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

}
