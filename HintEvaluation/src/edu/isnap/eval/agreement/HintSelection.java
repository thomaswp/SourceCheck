package edu.isnap.eval.agreement;

import static edu.isnap.dataset.AttemptAction.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.SnapParser.Filter;
import edu.isnap.parser.Store.Mode;

public class HintSelection {

	private final static double START = 0.2, CUTOFF = 0.6;
	private final static int DEFAULT_SEED = 1234;
	private final static Filter[] DEFAULT_FILTERS = {
			new SnapParser.LikelySubmittedOnly(),
	};

	public static void main(String[] args) throws IOException {
//		printFall2016();
//		printSpring2017EDM();
		printHintRating2017();
	}

	protected static void printFall2016() {
		Assignment[] assignments = {
				Fall2016.PolygonMaker,
				Fall2016.Squiral,
				Fall2016.GuessingGame1,
				Fall2016.GuessingGame2,
		};
		for (Assignment assignment : assignments) {
			printSelect("hitns", assignment, DEFAULT_FILTERS, "twprice", "rzhi");
		}
	}

	protected static void printSpring2017EDM() throws IOException {
		Assignment[] assignments = {
				Spring2017.Squiral,
				Spring2017.GuessingGame1,
		};
		Random rand = new Random(1234);
		for (Assignment assignment : assignments) {
			List<HintRequest> selected = selectEarlyLate(assignment, new SnapParser.Filter[] {
					new SnapParser.LikelySubmittedOnly(),
					new SnapParser.StartedAfter(Assignment.date(2017, 1, 29))
			}, true, rand);
			printSQL("handmade_hints", selected, "vmcatete", "nalytle", "ydong2");
			exportSelected(assignment, selected);
		}
	}

	protected static void printSpring2017() throws IOException {
		Assignment[] assignments = {
				Spring2017.PolygonMaker,
				Spring2017.Squiral,
				Spring2017.GuessingGame1,
				Spring2017.GuessingGame2,
		};
		for (Assignment assignment : assignments) {
			List<HintRequest> selected = selectEarlyLate(assignment, new SnapParser.Filter[] {
					new SnapParser.LikelySubmittedOnly(),
					new SnapParser.StartedAfter(Assignment.date(2017, 1, 29))
			});
			printSQL("hints", selected, "twprice", "rzhi");
			exportSelected(assignment, selected);
		}
	}

	protected static void printHintRating2017() throws IOException {
		Assignment[][] assignments = {
				new Assignment[] {
						Spring2017.PolygonMaker,
						Fall2017.PolygonMaker,
				}, new Assignment[] {
						Spring2017.Squiral,
						Fall2017.Squiral,
				}, new Assignment[] {
						Spring2017.GuessingGame1,
						Fall2017.GuessingGame1,
				}, new Assignment[] {
						Spring2017.GuessingGame2,
						Fall2017.GuessingGame2,
				}
		};

		int maxReuqestsPerAssignment = 30;

		for (Assignment[] assignmentSet : assignments) {
			Map<AssignmentAttempt,List<HintRequest>> requestMap = new HashMap<>();
			Arrays.stream(assignmentSet)
			.forEach(a -> requestMap.putAll(getAllHintRequests(a, DEFAULT_FILTERS, false)));

			System.out.println(assignmentSet[0].name + ": " + requestMap.size());
			Random rand = new Random(DEFAULT_SEED);
			List<HintRequest> selected;
			if (requestMap.size() >= maxReuqestsPerAssignment) {
				selected = requestMap.values().stream()
						.map(list -> list.get(rand.nextInt(list.size())))
						.collect(Collectors.toList());
				while (selected.size() > maxReuqestsPerAssignment) {
					selected.remove(rand.nextInt(selected.size()));
				}
			} else {
				// TODO: Actually handle this situation by randomly selecting some user with 2+
				// hints to give early/late hints
				throw new RuntimeException(
						"Less than max requests in assignment: " + assignmentSet[0].name);
			}
		}
	}

	protected static void exportSelected(Assignment assignment, List<HintRequest> selected)
			throws IOException {
		for (HintRequest request : selected) {
			AttemptAction action = request.action;
			JSONObject json = JsonAST.toJSON(action.lastSnapshot);
			JsonAST.write(String.format("%s/hint-selection/%s/%d.json",
					assignment.dataset.exportDir(), assignment.name, action.id), json.toString(2));
		}
		JsonAST.write(
				String.format("%s/hint-selection/%s-values.txt",
						assignment.dataset.exportDir(), assignment.name),
				String.join("\n", JsonAST.values));
	}

	public static boolean isHintDialogRow(AttemptAction action) {
		return SHOW_HINT_MESSAGES.contains(action.message);
	}

	public static boolean isHintRow(AttemptAction action) {
		return isHintDialogRow(action) ||
				HIGHLIGHT_CHECK_WORK.equals(action.message) ||
				(HIGHLIGHT_TOGGLE_INSERT.equals(action.message) &&
						action.data.equals("true"));
	}


	public static void printSelect(String table, Assignment assignment, SnapParser.Filter[] filters,
			String... users) {
		printSQL(table, selectEarlyLate(assignment, filters), users);
	}

	public static List<HintRequest> selectEarlyLate(Assignment assignment,
			SnapParser.Filter[] filters) {
		return selectEarlyLate(assignment, filters, false,
				new Random(assignment.name.hashCode() + DEFAULT_SEED));
	}

	public static List<HintRequest> selectEarlyLate(Assignment assignment,
			SnapParser.Filter[] filters, boolean hintDialogsOnly, Random rand) {

		Map<AssignmentAttempt, List<HintRequest>> requestMap = getAllHintRequests(assignment,
				filters, hintDialogsOnly);

		return selectEarlyLate(requestMap, rand);
	}

	private static List<HintRequest> selectEarlyLate(
			Map<AssignmentAttempt, List<HintRequest>> requestMap, Random rand) {
		List<HintRequest> selected = new ArrayList<>();
		for (List<HintRequest> requests : requestMap.values()) {
			if (requests == null) continue;

			List<HintRequest> earlyRequests = requests.stream()
					.filter(r -> r.isEarly()).collect(Collectors.toList());
			List<HintRequest> lateRequests = requests.stream()
					.filter(r -> !r.isEarly()).collect(Collectors.toList());

			// Sample one from early if possible, and late if not
			if (!sample(earlyRequests, selected, rand)) sample(lateRequests, selected, rand);
			// Sample one from late if possible, and early if not
			if (!sample(lateRequests, selected, rand)) sample(earlyRequests, selected, rand);
		}

		return selected;
	}

	private static Map<AssignmentAttempt, List<HintRequest>> getAllHintRequests(
			Assignment assignment, SnapParser.Filter[] filters, boolean hintDialogsOnly) {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true, filters);
		Map<AssignmentAttempt, List<HintRequest>> requestMap = new LinkedHashMap<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			List<HintRequest> requests = new ArrayList<>();
			HashSet<Node> added = new HashSet<>();
			long lastAddedTime = -1;
			for (AttemptAction action : attempt) {
				double percTime = (double) action.currentActiveTime / attempt.totalActiveTime;
				if (!(hintDialogsOnly ? isHintDialogRow(action) : isHintRow(action))) continue;
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
				HintRequest request = new HintRequest(assignment, attempt, action, percTime);
				requests.add(request);
			}
			if (requests.size() > 0) requestMap.put(attempt, requests);
		}
		return requestMap;
	}

	private static <T> boolean sample(List<T> list, List<T> selected, Random rand) {
		if (list.size() == 0) return false;
		selected.add(list.remove(rand.nextInt(list.size())));
		return true;
	}

	private static void printSQL(String table, List<HintRequest> selected, String... users) {
		for (HintRequest request : selected) {
			for (String user : users) {
				System.out.println(request.getSQLInsert(table, user));
			}
		}
	}

	public static class HintRequest {
		public final Assignment assignment;
		public final AssignmentAttempt attempt;
		public final AttemptAction action;
		public final double percTime;

		public HintRequest(Assignment assignment, AssignmentAttempt attempt, AttemptAction action,
				double percTime) {
			this.assignment = assignment;
			this.attempt = attempt;
			this.action = action;
			this.percTime = percTime;
		}

		public String getSQLInsert(String table, String user) {
			return String.format("INSERT INTO `%s` (`userID`, `rowID`) VALUES ('%s', %d);",
					table, user, action.id);
		}

		public boolean isEarly() {
			 return percTime < CUTOFF;
		}
	}
}
