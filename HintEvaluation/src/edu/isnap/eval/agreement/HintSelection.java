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
			exportSelected(assignment, "edm2017", selected);
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
			exportSelected(assignment, "earlyLate", selected);
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

		// Projects used in training
		String[] excludeProjectIDs = {
				// squiralHW
				"283643ab", "ffb7babe", "ab6da9de", "1b61f46a",
				// guess1Lab
				"3cbff372", "478fd3c4", "e3024ff8"
		};

		String[] users = {
				"twprice", "rzhi", "vmcatete", "nalytle", "ydong2"
		};

		int maxReuqestsPerAssignment = 30;

		boolean dialogOnly = false;

		for (Assignment[] assignmentSet : assignments) {
			Map<AssignmentAttempt,List<HintRequest>> requestMap = new HashMap<>();
			Arrays.stream(assignmentSet)
			.forEach(a -> requestMap.putAll(getAllHintRequests(a, DEFAULT_FILTERS, dialogOnly)));

			int originalSize = requestMap.size();
			// Remove any attempts that we used in trainings
			for (AssignmentAttempt attempt : new ArrayList<>(requestMap.keySet())) {
				if (Arrays.stream(excludeProjectIDs).anyMatch(id -> attempt.id.startsWith(id))) {
					requestMap.remove(attempt);
				}
			}

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
				List<List<HintRequest>>
					singletons = new ArrayList<>(),
					multiples = new ArrayList<>();
				selected = new ArrayList<>();

				// Split the projects into those with 1 hint request and those with multiple
				requestMap.values().forEach(v -> (v.size() == 1 ? singletons : multiples).add(v));

				// For the singletons, add their one hint, then clear the list
				singletons.forEach(list -> selected.addAll(list));
				singletons.clear();

				// For those the have multiple hints, some of them will give 2, some will give 1
				int twoCount = Math.min(
						maxReuqestsPerAssignment - requestMap.size(),
						multiples.size());
				int oneCount = multiples.size() - twoCount;

				// Randomly select some one-count projects and add a random hint request from them
				for (int i = 0; i < oneCount; i++) {
					singletons.add(multiples.remove(rand.nextInt(multiples.size())));
				}
				singletons.forEach(list -> selected.add(list.get(rand.nextInt(list.size()))));

				// For those that remain, select an early and a late one
				multiples.forEach(list -> selectEarlyLate(list, selected, rand));
			}

			System.out.printf("-- Selected %d hints from %d(%d) projects for %s\n",
					selected.size(), originalSize, requestMap.size(),
					assignmentSet[0].name);

//			CountMap<String> actions = new CountMap<>();
//			selected.forEach(req -> actions.increment(req.action.message));
//			System.out.println(actions);
//
//			CountMap<Integer> sections = new CountMap<>();
//			selected.forEach(req -> sections.increment(
//					req.action.currentActiveTime * 5 / req.attempt.totalActiveTime));
//			System.out.println(sections);

//			exportSelected(assignmentSet[0], "ratings2017-" + maxReuqestsPerAssignment, selected);

			List<HintRequest> spring2017 = new ArrayList<>(), fall2017 = new ArrayList<>();
			for (HintRequest req : selected) {
				(req.assignment.dataset instanceof Spring2017 ? spring2017 : fall2017).add(req);
			}
			// Be careful with this output, since it uses the USE directive
			System.out.println("USE snap_spring2017;");
			printSQL("handmade_hints", selected, users);
			System.out.println("USE snap;");
			printSQL("handmade_hints", selected, users);
		}
	}

	protected static void exportSelected(Assignment assignment, String folder,
			List<HintRequest> selected)
			throws IOException {
		for (HintRequest request : selected) {
			AttemptAction action = request.action;
			JSONObject json = JsonAST.toJSON(action.lastSnapshot);
			JsonAST.write(String.format("%s/hint-selection/%s/%s/%d.json",
					assignment.dataset.exportDir(), folder, assignment.name, action.id),
					json.toString(2));
		}
		JsonAST.write(
				String.format("%s/hint-selection/%s/%s-values.txt",
						assignment.dataset.exportDir(), folder, assignment.name),
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

		List<HintRequest> selected = new ArrayList<>();
		for (List<HintRequest> requests : requestMap.values()) {
			if (requests == null) continue;
			selectEarlyLate(requests, selected, rand);
		}

		return selected;
	}

	private static void selectEarlyLate(List<HintRequest> requests, List<HintRequest> selected,
			Random rand) {
		List<HintRequest> earlyRequests = requests.stream()
				.filter(r -> r.isEarly()).collect(Collectors.toList());
		List<HintRequest> lateRequests = requests.stream()
				.filter(r -> !r.isEarly()).collect(Collectors.toList());

		// Sample one from early if possible, and late if not
		if (!sample(earlyRequests, selected, rand)) sample(lateRequests, selected, rand);
		// Sample one from late if possible, and early if not
		if (!sample(lateRequests, selected, rand)) sample(earlyRequests, selected, rand);
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
			return String.format(
					"INSERT INTO `%s` (`userID`, `rowID`, `trueAssignmentID`) "
					+ "VALUES ('%s', %d, '%s');",
					table, user, action.id, assignment.name);
		}

		public boolean isEarly() {
			 return percTime < CUTOFF;
		}
	}
}
