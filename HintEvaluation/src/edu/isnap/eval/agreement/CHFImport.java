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
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.agreement.RateHints.GoldStandard;
import edu.isnap.eval.agreement.RateHints.HintOutcome;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.parser.Store.Mode;

public class CHFImport {

	public static void main(String[] args) throws IOException {
//		testLoad(Spring2017.GuessingGame1, "hint-rating");
//		testLoad(Spring2017.Squiral, "hint-rating"?);

		// TODO: 2 problems...
		// 1) The public dataset doesn't include literal values unless they're numbers, but the
		//    consensus dataset does. We'll have to remove them all first...
		// 2) The consensus dataset has sorted scripts and the CHF one doesn't seem to...
		GoldStandard standard = TutorEdits.readConsensus(
				Spring2017.instance, "consensus-gg-sq.csv");
		CHFHintSet hintSet = new CHFHintSet("chf", HighlightHintSet.SnapRatingConfig, "hint-rating",
				Spring2017.Squiral, Spring2017.GuessingGame1);

		RateHints.rate(standard, hintSet);


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

				Node from = JsonAST.toAST(action.lastSnapshot, true).toNode(SnapNode::new);
				String fromString = from.prettyPrint(true);
				System.out.println("--------- " + action.id + " ---------");
				System.out.println(fromString);
				for (CHFEdit edit : edits) {
					errors.add(edit.error);
					System.out.println(edit.priority + ": " + edit.error);
					System.out.println(Diff.diff(fromString, edit.outcome.prettyPrint(true), 2));
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
		public final Node outcome;
		public final double error;
		public final int rowID, priority;

		public CHFEdit(int rowID, Node outcome, double error, int priority) {
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
			CHFEdit edit = new CHFEdit(id, root.toNode(SnapNode::new), error, priority);
			return edit;
		}

		public double weight() {
			// TODO: Ask how to fix this
			return Math.max(-Math.log(Math.max(error + 0.5, 0.00001)), 0.0001);
		}

		public HintOutcome toOutcome() {
			return new HintOutcome(outcome, rowID, weight());
		}
	}
}
