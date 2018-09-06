package edu.isnap.eval;

import java.io.File;
import java.io.IOException;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;

public class PolygonAutoGrader {
	
	public static void main(String[] args) throws IOException {
		for (File xml : new File("tests").listFiles()) {
			if (!xml.getName().endsWith("polygon-solution102.xml"))
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
	
	// I divided the Polygon maker code into 3 features, Use of Ask block,
	// Use of PenDown , and Use of Repeat Block.

	public final static Grader[] PolygonGraders = new Grader[] { 
		new PolygonGraderAsk(),
		new PolygonGraderPenDown(),
		new PolygonGraderRepeat(), 
	};

	
	public static class PolygonGraderAsk implements Grader {

		@Override
		public String name() {
			return "Ask";
		}

		// step 1
		private final static Predicate backbone = new Node.BackbonePredicate("sprite", "script");

		private final static Predicate hasAskName = new Predicate() {
			@Override
			public boolean eval(Node node) {
				// if ask block exists in the script, return true.
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				return ask >= 0;
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasAskName);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	// check if pen down exists, if yes, need to check if it's before or after the
	// move block.
	public static class PolygonGraderPenDown implements Grader {
		@Override
		public String name() {
			return "PenDown";
		}

		private final static Predicate backbone = new Node.BackbonePredicate("sprite", "script");
		private final static Predicate isPenDown = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int downIndex = node.searchChildren(new Node.TypePredicate("down"));
				if (downIndex < 0) // if no pen down, return false
					return false;

				// check if there is forward (move block) index
				int forwardIndex = node.searchChildren(new Node.TypePredicate("forward"));
				if (forwardIndex < 0) // this means forward is not a child of down, so it might be nested in repeat
				{
					int repeatIndex = node.searchChildren(new Node.TypePredicate("doRepeat"), downIndex + 1);
					if (repeatIndex < 0) // if this is true, then repeat is not after the pen down, then return false
						return false;

				} // else repeat block exists, and it might have forward nested to it.
					// Note: In PolygonGraderRepeat I handled if pen down is inside the repeat
					// block.
				else if (forwardIndex < downIndex) // if pendown is after the forward block, then return false
					return false;

				return true;
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, isPenDown);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

// Test the repeat block
	public static class PolygonGraderRepeat implements Grader {
		@Override
		public String name() {
			return "Repeat";
		}

		private final static Predicate backbone = new Node.BackbonePredicate("sprite|customBlock", "script", "...",
				"doRepeat");
		private final static Predicate isRepeat = new Predicate() {
			@Override
			public boolean eval(Node node) {
				// repeat block must have at least 2 blocks inside (the answer + script).
				if (node.children.size() < 2)
					return false;

				// check that it repeats on the answer.
				String t1 = node.children.get(0).type();
				if (!"getLastAnswer".equals(t1))
					return false;

				Node scriptNode = node.children.get(1); // get the script inside the repeat block

				// repeat block must have at least 2 blocks inside.
				if (scriptNode.children.size() < 2)
					return false;

				if (scriptNode.children.size() >= 2) { // turn and forward must exist in the repeat, if any doesn't
														// exist then return false
					if (!scriptNode.childHasType("turn", scriptNode.index()) ||
						scriptNode.childHasType("forward", scriptNode.index()))
						return false;

					// if forward and pen down are in the repeat block
					if (scriptNode.childHasType("forward", scriptNode.index())
							&& scriptNode.childHasType("down", scriptNode.index())) {
						int index1 = scriptNode.searchChildren(new Node.TypePredicate("forward"));
						int index2 = scriptNode.searchChildren(new Node.TypePredicate("down"));
						if (index2 >= index1) // this means PenDown is after move, then return false
							return false;
					}
				}

				// Now checking the turn block inside the repeat block

				// retrieving the turn node to check its children.
				int index3 = scriptNode.searchChildren(new Node.TypePredicate("turn"));
				Node nodeTurn = scriptNode.children.get(index3);

				if (nodeTurn == null)
					return false;
				else { // turn node must have only one child (the Quotient block)
					if (nodeTurn.children.size() != 1)
						return false;

					String t2 = nodeTurn.children.get(0).type();
					if (!"reportQuotient".equals(t2))
						return false;
					else {
						Node reportQuotientNode = nodeTurn.children.get(0);

						if (reportQuotientNode.children.size() != 2)
							return false;

						String t3 = reportQuotientNode.children.get(0).value(); // here the value must be 360
						String t4 = reportQuotientNode.children.get(1).type(); // here the type must be "getLastAnswer"

						if (!"360".equals(t3))
							return false;
						if (!"getLastAnswer".equals(t4))
							return false;
					}
				}
				return true; // if all conditions are met.
			}
		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, isRepeat);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}
}
