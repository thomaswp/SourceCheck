package edu.isnap.datasets.run;

import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.datasets.HelpSeeking;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class RunPrintSubmitted {
	public static void main(String[] args) {
		print(HelpSeeking.BrickWall);
	}

	public static void print(Assignment assignment) {
		System.out.println("Attempts for: " + assignment);
		System.out.println();
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, true);
		for (AssignmentAttempt attempt : attempts.values()) {
			Snapshot submitted = attempt.submittedSnapshot;
			if (submitted == null) continue;

			System.out.println(attempt.id);
			System.out.println(submitted.toCode(false));
		}
	}
}
