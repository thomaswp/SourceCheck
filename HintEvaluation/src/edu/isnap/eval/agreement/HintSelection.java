package edu.isnap.eval.agreement;

import static edu.isnap.dataset.AttemptAction.SHOW_HINT_MESSAGES;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class HintSelection {

	private final static double START = 0.2, CUTOFF = 0.6;
	private final static Random rand = new Random(1234);

	public static void main(String[] args) throws IOException {
//		printFall2016();
		printSpring2017();
	}

	protected static void printFall2016() {
		Assignment[] assignments = {
				Fall2016.Squiral,
				Fall2016.GuessingGame1
		};
		for (Assignment assignment : assignments) {
			printSelect(assignment, new SnapParser.Filter[] {
					new SnapParser.SubmittedOnly(),
			}, "twprice", "rzhi");
		}
	}

	protected static void printSpring2017() throws IOException {
		Assignment[] assignments = {
				Spring2017.Squiral,
				Spring2017.GuessingGame1
		};
		for (Assignment assignment : assignments) {
			List<AttemptAction> selected = select(assignment, new SnapParser.Filter[] {
					new SnapParser.LikelySubmittedOnly(),
					new SnapParser.StartedAfter(Assignment.date(2017, 1, 29))
			});
			printSQL(selected, "twprice", "rzhi");
			exportSelected(assignment, selected);
		}
	}

	protected static void exportSelected(Assignment assignment, List<AttemptAction> selected)
			throws IOException {
		for (AttemptAction action : selected) {
			JSONObject json = JsonAST.toJSON(action.lastSnapshot);
			File file = new File(String.format("%s/hint-selection/%s/%d.json",
					assignment.dataset.exportDir(), assignment.name, action.id));
			file.getParentFile().mkdirs();
			FileWriter writer = new FileWriter(file);
			writer.write(json.toString(2));
			writer.close();
		}
	}

	public static boolean isHintRow(AttemptAction action) {
		return SHOW_HINT_MESSAGES.contains(action.message);
	}

	public static void printSelect(Assignment assignment, SnapParser.Filter[] filters,
			String... users) {
		printSQL(select(assignment, filters), users);
	}

	public static List<AttemptAction> select(Assignment assignment, SnapParser.Filter[] filters) {

		List<AttemptAction> selected = new ArrayList<>();
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true, filters);

		for (AssignmentAttempt attempt : attempts.values()) {
			List<AttemptAction> earlyHints = new ArrayList<>();
			List<AttemptAction> lateHints = new ArrayList<>();
			HashSet<Node> added = new HashSet<>();
			long lastAddedTime = -1;
			for (AttemptAction action : attempt) {
				double percTime = (double) action.currentActiveTime / attempt.totalActiveTime;
				if (!isHintRow(action)) continue;
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
				if (percTime < CUTOFF) earlyHints.add(action);
				else lateHints.add(action);
			}

			// Sample one from early if possible, and late if not
			if (!sample(earlyHints, selected)) sample(lateHints, selected);
			// Sample one from late if possible, and early if not
			if (!sample(lateHints, selected)) sample(earlyHints, selected);
		}

		return selected;
	}

	private static boolean sample(List<AttemptAction> list, List<AttemptAction> selected) {
		if (list.size() == 0) return false;
		selected.add(list.remove(rand.nextInt(list.size())));
		return true;
	}

	private static void printSQL(List<AttemptAction> selected, String... users) {
		for (AttemptAction action : selected) {
		for (String user : users) {
				System.out.printf(
						"INSERT INTO `hints` (`userID`, `rowID`) VALUES ('%s', %d);\n",
						user, action.id);
			}
		}
	}
}
