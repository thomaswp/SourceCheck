package edu.isnap.eval.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.datasets.Fall2018;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class VisualizationTest {

	public static Assignment testData = Fall2018.PolygonMaker;

	public static List<AssignmentAttempt> selectAttemptsFromDatabase(
			Assignment assignment) throws Exception {
		SnapParser parser = new SnapParser(assignment, Mode.Ignore, false);
		String[] ids = null;
		String[] names = null;
		String[] times = {"2019-01-01"};

//		Map<String, AssignmentAttempt> attempts =
//				parser.parseActionsFromDatabase(testData.name, ids, names);
		Map<String, AssignmentAttempt> attempts =
				parser.parseActionsFromDatabaseWithTimestamps(testData.name, ids, names, times);
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			selected.add(attempt);
		}
		return selected;
	}
//		List<AssignmentAttempt> attempts = selectAttempts(testData);
//		System.out.println(attempts.size());
//		for (AssignmentAttempt attempt : attempts) {
//
////			if (!attempt.id.equals("ba36c1cc-9e60-4c29-aef6-d07b20d11f6f")) continue;
//			// for each project (submission)
//			System.out.println(attempt.id);
//			System.out.println(attempt.size());
//			System.out.println(attempt.timeSegments);
////			for (AttemptAction action : attempt) {
////				System.out.println(action.message);
////				if (action.snapshot == null) continue;
////				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
//////				System.out.println(node.prettyPrint(true));
////			}
//		}

	public static List<AssignmentAttempt> selectAttempts(Assignment assignment) {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			selected.add(attempt);
		}
		return selected;
	}

	/***
	 * this function takes a snapshot and returns the tree size of the resulting AST
	 * @param lastSnapshot is the last snapshot in a given attempt
	 * @return the size of the resulting abstract syntax tree
	 */
	public static int getTreeSize(Snapshot lastSnapshot) {
		Node node = SimpleNodeBuilder.toTree(lastSnapshot, true);
		return node.treeSize();
	}

	public static void main(String[] args) throws Exception {

		// TODO: Compare the result of doing this from database and read from files

		System.out.println("Database:");
//		List<AssignmentAttempt> attempts2 = selectAttempts(testData);
		List<AssignmentAttempt> attempts2 = selectAttemptsFromDatabase(testData);
		System.out.println(attempts2.size());
		System.out.println("here");
		for (AssignmentAttempt attempt : attempts2) {

//			if (!attempt.id.equals("ba36c1cc-9e60-4c29-aef6-d07b20d11f6f")) continue;
			//BUG: ce5b3694-79f4-41ad-9712-3716e8b98877 cannot be found, since its assignmentID is none.
			// for each project (submission)
			if (attempt.size() == 0) continue;

			Snapshot lastSnapshot = attempt.rows.getLast().lastSnapshot;
			//System.out.println(lastSnapshot.toCode());
			System.out.println("tree size: " + getTreeSize(lastSnapshot));
			System.out.println(attempt.id);
			System.out.println(attempt.size());

//			for (AttemptAction action : attempt) {
//				System.out.println(action.message);
//				if (action.snapshot == null) continue;
//				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
////				System.out.println(node.prettyPrint(true));
//			}
		}

	}

}
