package edu.isnap.eval.tutor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.parser.Store.Mode;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintOutcome;
import edu.isnap.rating.HintOutcome.HintWithError;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RateHints;

public class CHFImport {

	public static void main(String[] args) throws IOException {
//		testLoad(Spring2017.GuessingGame1, "hint-rating");
//		testLoad(Spring2017.Squiral, "hint-rating");

		HintSet hintSet = HintSet.fromFolder("chf", HighlightHintSet.SnapRatingConfig,
				Spring2017.dataDir + "/chf/hint-rating-with-past",
				Spring2017.Squiral.name, Spring2017.GuessingGame1.name);

		GoldStandard fall2016Standard = TutorEdits.readConsensus(
				Fall2016.instance, "consensus-gg-sq.csv");
		GoldStandard spring2017Standard = TutorEdits.readConsensus(
				Spring2017.instance, "consensus-gg-sq.csv");

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

		HintSet allHints = HintSet.fromFolder("chf", HighlightHintSet.SnapRatingConfig,
				assignment.dir("chf/" + folder), assignment.name);
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			for (AttemptAction action : attempt) {
				List<HintOutcome> outcomes = allHints.getOutcomes(action.id);
				if (outcomes == null) continue;

				ASTNode from = JsonAST.toAST(action.lastSnapshot, true);
				String fromString = SnapNode.prettyPrint(from, true);
				System.out.println("--------- " + action.id + " ---------");
				System.out.println(fromString);
				for (int i = 0; i < outcomes.size(); i++) {
					HintOutcome outcome = outcomes.get(i);
					System.out.println(i + ": " + ((HintWithError) outcome).error);
					System.out.println(Diff.diff(fromString,
							SnapNode.prettyPrint(outcome.outcome, true), 2));
					System.out.println();
				}

			}
		}

	}
}
