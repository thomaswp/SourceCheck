package edu.isnap.eval.agreement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.parser.Store.Mode;
import edu.isnap.rating.RateHints;
import edu.isnap.rating.RateHints.GoldStandard;
import edu.isnap.rating.RateHints.HintOutcome;

public class CHFImport {

	public static void main(String[] args) throws IOException {
//		testLoad(Spring2017.GuessingGame1, "hint-rating");
//		testLoad(Spring2017.Squiral, "hint-rating");

		GoldStandard fall2016Standard = TutorEdits.readConsensus(
				Fall2016.instance, "consensus-gg-sq.csv");
		GoldStandard spring2017Standard = TutorEdits.readConsensus(
				Spring2017.instance, "consensus-gg-sq.csv");

		CHFHintSet hintSet = new CHFHintSet("chf", HighlightHintSet.SnapRatingConfig,
				"hint-rating-with-past",
				Spring2017.Squiral, Spring2017.GuessingGame1);

		System.out.println("Fall 2016");
		RateHints.rate(fall2016Standard, hintSet);
		System.out.println("Spring 2017");
		RateHints.rate(spring2017Standard, hintSet);


	}

	protected static void testLoad(Assignment assignment, String folder) throws IOException {
		File directory = new File(assignment.dir("chf/" + folder));
		if (!directory.exists()) {
			System.out.println("No directory: " + directory.getAbsolutePath());
			return;
		}

		List<Double> errors = new ArrayList<>();

		ListMap<Integer, CHFEdit> allHints = loadAllHints(directory);
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			for (AttemptAction action : attempt) {
				List<CHFEdit> edits = allHints.get(action.id);
				if (edits == null) continue;

				ASTNode from = JsonAST.toAST(action.lastSnapshot, true);
				String fromString = SnapNode.prettyPrint(from, true);
				System.out.println("--------- " + action.id + " ---------");
				System.out.println(fromString);
				for (CHFEdit edit : edits) {
					errors.add(edit.error);
					System.out.println(edit.priority + ": " + edit.error);
					System.out.println(Diff.diff(fromString,
							SnapNode.prettyPrint(edit.outcome, true), 2));
					System.out.println();
				}

			}
		}

		Collections.sort(errors);
		System.out.println(errors);
		System.out.println("Min: " + errors.get(0));
		System.out.println("Max: " + errors.get(errors.size() - 1));

	}

	public static ListMap<Integer, CHFEdit> loadAllHints(Assignment assignment, String folder)
			throws IOException {
		return loadAllHints(new File(assignment.dir("chf/" + folder)));
	}

	public static ListMap<Integer, CHFEdit> loadAllHints(File directory) throws IOException {
		ListMap<Integer, CHFEdit> hints = new ListMap<>();
		for (File file : directory.listFiles()) {
			CHFEdit edit = CHFEdit.parse(file);
			List<CHFEdit> list = hints.getList(edit.rowID);
			list.add(edit);
			list.sort((a, b) -> Integer.compare(a.priority, b.priority));
		}
		return hints;
	}

	public static class CHFEdit {
		public final ASTNode outcome;
		public final double error;
		public final int rowID, priority;

		public CHFEdit(int rowID, ASTNode outcome, double error, int priority) {
			this.rowID = rowID;
			this.outcome = outcome;
			this.error = error;
			this.priority = priority;
		}

		public static CHFEdit parse(File file) throws IOException {
			String contents = new String(Files.readAllBytes(file.toPath()));
			JSONObject json = new JSONObject(contents);
			double error = json.getDouble("error");
			ASTNode root = ASTNode.parse(json);
			String name = file.getName().replace(".json", "");
			int underscoreIndex = name.indexOf("_");
			int id = Integer.parseInt(name.substring(0, underscoreIndex));
			int priority = Integer.parseInt(name.substring(underscoreIndex + 1));
			CHFEdit edit = new CHFEdit(id, root, error, priority);
			return edit;
		}

		public double weight(double minError, double beta) {
			double weight = Math.exp(-beta * (error - minError));
			if (Double.isNaN(weight)) {
				throw new RuntimeException(String.format(
						"Weight is Nan: e=%.05f; beta=%.05f; e_min=%.05f",
						error, beta, minError));
			}
			return weight;
		}

		public HintOutcome toOutcome(double minError, double beta) {
			return new HintOutcome(outcome, rowID, weight(minError, beta));
		}
	}
}
