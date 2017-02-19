package edu.isnap.eval.agreement;

import static edu.isnap.dataset.AttemptAction.SHOW_HINT_MESSAGES;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class HintSelection {

	private final static double START = 0.2, CUTOFF = 0.6;
	private final static Random rand = new Random(1234);

	public static void main(String[] args) {
//		printFall2016();
		printSpring2017();
	}

	protected static void printFall2016() {
		Assignment[] assignments = {
				Fall2016.Squiral,
				Fall2016.GuessingGame1
		};
		for (Assignment assignment : assignments) {
			select(assignment, new SnapParser.Filter[] {
					new SnapParser.SubmittedOnly(),
			}, "twprice", "rzhi");
		}
	}

	protected static void printSpring2017() {
		Assignment[] assignments = {
				Spring2017.Squiral,
				Spring2017.GuessingGame1
		};
		for (Assignment assignment : assignments) {
			select(assignment, new SnapParser.Filter[] {
					new SnapParser.LikelySubmittedOnly(),
					new SnapParser.StartedAfter(Assignment.date(2017, 1, 29))
			}, "twprice", "rzhi");
		}
	}

	public static void select(Assignment assignment, SnapParser.Filter[] filters, String... users) {

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true, filters);

		for (AssignmentAttempt attempt : attempts.values()) {
			List<Integer> earlyHints = new ArrayList<>();
			List<Integer> lateHints = new ArrayList<>();
			HashSet<Node> added = new HashSet<>();
			long lastAddedTime = -1;
			for (AttemptAction action : attempt) {
				double percTime = (double) action.currentActiveTime / attempt.totalActiveTime;
				if (!SHOW_HINT_MESSAGES.contains(action.message)) continue;
				if (percTime < START) continue;

				// Skip hints within 30 seconds of the last one we added
				if (lastAddedTime != -1 && action.timestamp.getTime() - lastAddedTime < 1000 * 30) {
					continue;
				}

				// Skip hints for code for which we've already added a hint
				Node node = SimpleNodeBuilder.toTree(action.lastSnapshot, true);
				if (!added.add(node)) {
					continue;
				}

				lastAddedTime = action.timestamp.getTime();
				if (percTime < CUTOFF) earlyHints.add(action.id);
				else lateHints.add(action.id);
			}

			// Sample one from early if possible, and late if not
			if (!sample(earlyHints, users)) sample(lateHints, users);
			// Sample one from late if possible, and early if not
			if (!sample(lateHints, users)) sample(earlyHints, users);
		}
	}

	private static boolean sample(List<Integer> list, String[] users) {
		if (list.size() == 0) return false;

		int index = rand.nextInt(list.size());
		int id = list.remove(index);

		for (String user : users) {
			System.out.printf(
					"INSERT INTO `hints` (`userID`, `rowID`) VALUES ('%s', %d);\n",
					user, id);
		}

		return true;
	}
}
