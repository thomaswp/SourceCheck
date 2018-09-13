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
			if (!xml.getName().endsWith("triangle13.xml"))
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


	// I divided the triangleSeries maker code into 3 features, Use of Ask block, Use of PenDown , and Use of Repeat Block.

	// this global variable, to make check on PenDown in the repeat block
	public static boolean PenDowninRepeat=false;

	public final static Grader[] PolygonTriangleSeriesGraders = new Grader[] {
		new TriangleSeriesGraderAsk(),
		new TriangleSeriesGraderRepeat(),
		new TriangleSeriesGraderPenDown(),


	};


	public static class TriangleSeriesGraderAsk implements Grader {

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
	public static class TriangleSeriesGraderPenDown implements Grader {
		@Override
		public String name() {
			return "PenDown";
		}

		private final static Predicate backbone = new Node.BackbonePredicate("sprite", "script");
		private final static Predicate isPenDown = new Predicate() {
			@Override
			public boolean eval(Node node)  // just check if penDown exists or not
			{
				int downIndex = node.searchChildren(new Node.TypePredicate("down"));
				if (downIndex < 0) // if no pen down in the main script
				{
					if(PenDowninRepeat==false) // check if it's not in the repeat as well
					   return false;
					else
						return true;
				}

				int repeatIndex = node.searchChildren(new Node.TypePredicate("doRepeat"), downIndex + 1);
				if (repeatIndex < 0) // if this is true, then repeat is not after the pen down, then return false
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
	public static class TriangleSeriesGraderRepeat implements Grader {
		@Override
		public String name() {
			return "Repeat";
		}

		// check if the student repeats 360/3 or 120
		private static boolean checkTurnLiterals(Node nestedTurnNode)
		{
			   String t3 = nestedTurnNode.children.get(0).value();
			   String t4 = nestedTurnNode.children.get(0).type();
			   if ("120".equals(t3))
			      return true;
			   else if("reportQuotient".equals(t4))
			   {
				   Node reportQuotientNode = nestedTurnNode.children.get(0);
				   String t5 = reportQuotientNode.children.get(0).value(); // here the value must be 360
				   String t6 = reportQuotientNode.children.get(1).value();
				   if ("360".equals(t5) && "3".equals(t6))
						return true;
				   else
					   return false;

			   }
			   else
			     return false;
		}

		// check if the student draws a triangle iteratively not inside a repeat
		private static boolean checkRepeatIteratively(Node nestedRepeatNode)
		{
			//move, turn, move, turn, move, turn
			if (nestedRepeatNode.children.size() < 6)
			   return false;
			else
			{
				int indexForward = nestedRepeatNode.searchChildren(new Node.TypePredicate("forward"),0);
				int indexTurn = nestedRepeatNode.searchChildren(new Node.TypePredicate("turn"),0);
				int indexTurnLeft = nestedRepeatNode.searchChildren(new Node.TypePredicate("turnLeft"),0);
				int indexDown = nestedRepeatNode.searchChildren(new Node.TypePredicate("down"),0,indexForward);
				if(indexDown>-1)
				{
					if(indexDown<indexForward)
						PenDowninRepeat=true;
				}
				if(indexForward>-1 && (indexTurn>-1 || indexTurnLeft>-1))
				{
					int startIndex=0;
					// it works for special conditions, need to think about in a better way!!
					if(indexTurn>indexForward || indexTurnLeft>indexForward)
						startIndex= indexForward;
					else if(indexTurn==-1)
						startIndex= indexTurnLeft;
					else
						startIndex= indexTurn;

					for(int i=startIndex; i<(startIndex+6); i+=2)
						{
							String t1 = nestedRepeatNode.children.get(i).type();
							String t2 = nestedRepeatNode.children.get(i+1).type();

							if ("forward".equals(t1) && ("turn".equals(t2) || "turnLeft".equals(t2)))
								{
						   // send the turn node to checkRepeatNumber
									boolean check = checkTurnLiterals(nestedRepeatNode.children.get(i+1));
									if(check==false)
										return false;

								}
							else if("forward".equals(t2) && ("turn".equals(t1)|| "turnLeft".equals(t1)))
								{
									boolean check = checkTurnLiterals(nestedRepeatNode.children.get(i));
									if(check==false)
										return false;
								}
							else
								return false;
						}
				}
				else
					return false;

			}
			return true;
		}

		// check if the student draws a triangle in a nested loop
		private static boolean checkNestedLoop(Node nestedRepeatNode)
		{
			if (nestedRepeatNode.children.size() < 2)
				return false;

			String t1 = nestedRepeatNode.children.get(0).value();
			if (!"3".equals(t1)) // if the loop is not looping 3 times, then return false
				return false;

			Node scriptNode = nestedRepeatNode.children.get(1); // get the script of the nested repeated

//			if (scriptNode.children.size() >= 2)
//			{ // turn and forward must exist in the repeat, if any doesn't exist then return false
			if (scriptNode.searchChildren(new Node.TypePredicate("forward"))==-1 &&
				(scriptNode.searchChildren(new Node.TypePredicate("turn"))==-1 || scriptNode.searchChildren(new Node.TypePredicate("turnLeft"))==-1 ))
					return false;
//			}

			// Now checking the turn block inside the repeat block

			// retrieving the turn node to check its children.
			int index3 = scriptNode.searchChildren(new Node.TypePredicate("turn"));
			if(index3>-1)
			{
				Node nodeTurn = scriptNode.children.get(index3);
				return checkTurnLiterals(nodeTurn);
			}
			else
			{
				 index3 = scriptNode.searchChildren(new Node.TypePredicate("turnLeft"));
				 if (index3 >= 0) {
					 Node nodeTurn = scriptNode.children.get(index3);
					 return checkTurnLiterals(nodeTurn);
				 } else {
					 return false;
				 }
			}
			//return checkTurnLiterals(nodeTurn); // check if the literals are 360/3 or 120
//			String t3 = nodeTurn.children.get(0).value();
//			if (!"120".equals(t3))
//				return false;

			//return true; // if all conditions are met.

	     }

		// check for the extra forward after drawing 1 triangle
        private static boolean checkExtraForward(Node scriptNodeFirstRepeat)
        {
        	if(scriptNodeFirstRepeat.children.size()>6) // check for the extra forward
			{
        		// possibilities!!
			  int indexForward = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("forward"),6);
			  int indexUp = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("up"),6);
			  int indexDown = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("down"),6);
			  if(indexForward==-1)
				  return false;
			  else if(indexUp>-1) // if there exist up, it must be before forward
			  {
				  if(indexForward>indexUp && indexDown>indexUp && indexDown>indexForward)
				      return true;
				  else
					  return false;
			  }
			  else
				  return true;
			}
        	else
        		return false;
        }
		private final static Predicate backbone = new Node.BackbonePredicate("sprite", "script", "...", "doRepeat");
		private final static Predicate isRepeat = new Predicate()
		{
			@Override
			public boolean eval(Node node) {
				// repeat block must have at least 3 blocks inside (the answer + script).
				if (node.children.size() < 2)
					return false;

				// check that it repeats on the answer.
				String t1 = node.children.get(0).type();
				if (!"getLastAnswer".equals(t1) && !"literal".equals(t1))
					return false;
////////////////////////////////////////////////////////////////////////////////////////
				Node scriptNodeFirstRepeat = node.children.get(1); // get the script inside the repeat block

				// repeat block must have at least 2 blocks inside (repeat + forward),
				//or it can have several move and turn.
				if (scriptNodeFirstRepeat.children.size() < 2)
					return false;
				else
				{
					// this mean it doesn't have a repeat , so check if it has iterative loop.
					if(scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("doRepeat"))==-1)
					{
						boolean iterativeRepeats= checkRepeatIteratively(scriptNodeFirstRepeat);
						if(iterativeRepeats) // if it has an iterative repeat, check the extra forward
						{
							return checkExtraForward(scriptNodeFirstRepeat);
//
						}
					} // there exist a nested repeatblock, and then there must be forward block
					if(scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("doRepeat"))>-1
							&& scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("forward"))>-1)
					{
						int index4 = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("doRepeat"));
						Node nestedRepeatNode = scriptNodeFirstRepeat.children.get(index4); // get the nested repeat block
						if(!checkNestedLoop(nestedRepeatNode)) // check if the nested loop is correct or not
							return false;
						if(scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("down"))>-1) // check the index of pendown if it exists inside the first repeat block
						{
							int index1 = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("forward"));
							int index2 = scriptNodeFirstRepeat.searchChildren(new Node.TypePredicate("down"));
							if (index2 < index1) // this means PenDown is before move, then set PenDowninRepeat to true
								PenDowninRepeat=true;

						}
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
