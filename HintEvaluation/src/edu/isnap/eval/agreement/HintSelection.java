package edu.isnap.eval.agreement;

import static edu.isnap.dataset.AttemptAction.SHOW_HINT_MESSAGES;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Spring2017;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class HintSelection {

	private final static double START = 0.2, CUTOFF = 0.6;
	private final static Random rand = new Random(1234);

	public static void main(String[] args) {
		Assignment[] assignments = {
				Spring2017.Squiral,
				Spring2017.GuessingGame1
		};
		for (Assignment assignment : assignments) {
			select(assignment, "twprice");
		}
	}

	public static void select(Assignment assignment, String... users) {

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.LikelySubmittedOnly(),
				// Filter out testing by the lab until I can get actual submission data
				new SnapParser.StartedAfter(Assignment.date(2017, 1, 29)));

		for (AssignmentAttempt attempt : attempts.values()) {
			List<Integer> earlyHints = new ArrayList<>();
			List<Integer> lateHints = new ArrayList<>();
			for (AttemptAction action : attempt) {
				double percTime = (double) action.currentActiveTime / attempt.totalActiveTime;
				if (!SHOW_HINT_MESSAGES.contains(action.message)) continue;
				if (percTime < START) continue;
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
