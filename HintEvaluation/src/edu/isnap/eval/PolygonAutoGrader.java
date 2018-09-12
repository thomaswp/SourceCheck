package edu.isnap.eval;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.MTurk2018;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

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

		Assignment assignment = MTurk2018.PolygonMakerSimple;
		//System.out.print(assignment);
		analyzeSyntaxTree(assignment);

	}

	public static void analyzeSyntaxTree(Assignment assignment) throws FileNotFoundException, IOException
	{
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Ignore, false, true);
		int attemptCount = 0;
		int snapshotCount = 0;
		int i=0;
		for (AssignmentAttempt attempt: attempts.values())
		{
			++attemptCount;
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
		 i++;

		}
		//System.out.println("attempt #:"+attemptCount);
		//System.out.println("snapshot #:"+snapshotCount);
	}

	// this global variable, to make check on PenDown in the repeat block
	public static boolean PenDowninRepeat=false;

	// I divided the Polygon maker code into 3 features, Use of Ask block,
	// Use of PenDown , and Use of Repeat Block.

	public final static Grader[] PolygonGraders = new Grader[] {
			new PolygonGraderAskAndUseAnswer(),
			new PolygonGraderPenDown(),
			new PolygonGraderRepeat(),


	};

	public static class PolygonGraderAskAndUseAnswer implements Grader {

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
				if (ask < 0) return false;
				int repeatIndex = node.searchChildren(new Node.TypePredicate("doRepeat"));
				if (repeatIndex < ask) return false;
				Node repeat = node.children.get(repeatIndex);
				int answerIndex = repeat.searchChildren(new Node.TypePredicate("answer"));
				return answerIndex == 0;
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
				if (downIndex < 0) // if no pen down, check if it exists in the repeat block
				{
					if(PenDowninRepeat==false) // check if it's not in the repeat as well
						return false;
					else
						return true;
				}

				// if pen down exists, then check if it's before or after repeat. if after it then return false
				int repeatIndex = node.searchChildren(new Node.TypePredicate("doRepeat"));
				if(repeatIndex>-1) // repeat exists in this script, then check if pen down is after it.
				{
					int repeatIndex2 = node.searchChildren(new Node.TypePredicate("doRepeat"), downIndex + 1);
					if (repeatIndex2 < 0) // if this is true, then pen down is after repeat, then return false
						return false;
				}

				return true; // all conditions are met!!
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
				else
				{ // turn and forward must exist in the repeat, if any doesn't
					// exist then return false
					if(scriptNode.searchChildren(new Node.TypePredicate("forward"))==-1
							|| scriptNode.searchChildren(new Node.TypePredicate("turn"))==-1)
						return false;

					// if forward and pen down are in the repeat block
					if(scriptNode.searchChildren(new Node.TypePredicate("down"))>-1)
					{
						int index1 = scriptNode.searchChildren(new Node.TypePredicate("forward"));
						int index2 = scriptNode.searchChildren(new Node.TypePredicate("down"));
						if (index2 < index1) // this means PenDown is before move, then set PenDowninRepeat to true
							PenDowninRepeat=true;
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
