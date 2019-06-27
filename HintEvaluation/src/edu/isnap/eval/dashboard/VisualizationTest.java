package edu.isnap.eval.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.datasets.Fall2018;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class VisualizationTest {

	public static Assignment testData = Fall2018.PolygonMaker;

	public static List<AssignmentAttempt> selectAttemptsFromDatabase(
			Assignment assignment) throws Exception {
		SnapParser parser = new SnapParser(assignment, Mode.Ignore, true);
		String[] ids = null;
		String[] names = null;


		Map<String, AssignmentAttempt> attempts =
				parser.parseActionsFromDatabase(testData.name, ids, names);
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			selected.add(attempt);
		}
		return selected;
	}

	public static List<AssignmentAttempt> selectAttempts(Assignment assignment) {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			selected.add(attempt);
		}
		return selected;
	}

	public static void main(String[] args) throws Exception {

		// TODO: Compare the result of doing this from database and read from files

		List<AssignmentAttempt> attempts = selectAttempts(testData);
		System.out.println(attempts.size());
		for (AssignmentAttempt attempt : attempts) {

//			if (!attempt.id.equals("ba36c1cc-9e60-4c29-aef6-d07b20d11f6f")) continue;
			// for each project (submission)
			System.out.println(attempt.id);
			System.out.println(attempt.size());
//			for (AttemptAction action : attempt) {
//				System.out.println(action.message);
//				if (action.snapshot == null) continue;
//				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
////				System.out.println(node.prettyPrint(true));
//			}
		}

		List<AssignmentAttempt> attempts2 = selectAttemptsFromDatabase(testData);
		System.out.println(attempts2.size());
		for (AssignmentAttempt attempt : attempts2) {

//			if (!attempt.id.equals("ba36c1cc-9e60-4c29-aef6-d07b20d11f6f")) continue;
			//BUG: ce5b3694-79f4-41ad-9712-3716e8b98877 cannot be found, since its assignmentID is none.
			// for each project (submission)
			if (attempt.size() == 0) continue;
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
