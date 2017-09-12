package edu.isnap.eval.grader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.HelpSeeking;
import edu.isnap.parser.Store.Mode;

public class GuessSubmitted {

	private final static String URL = "arena.csc.ncsu.edu/hs/logging/view/display.php";
	private final static long MAX_GAP_HINTS = 1000 * 60;
	private final static long MAX_GAP_TUTOR = 1000 * 60 * 4;

	public static void main(String[] args) throws IOException {
		for (Assignment assignment : HelpSeeking.All) {
			File file = new File(assignment.analysisDir() + "/submitted.txt");
			file.getParentFile().mkdirs();
			PrintWriter writer = new PrintWriter(file);
			for (AssignmentAttempt attempt : assignment.load(Mode.Use, false, false).values()) {
				long last = attempt.rows.getFirst().timestamp.getTime();
				int i = 0;
				int lastSnapshotID = 0, lastRow = 0;
				double cutGap = 0;
				boolean hints = false;
				for (AttemptAction action : attempt) {
					if (action.message.equals(AttemptAction.HINT_PROCESS_HINTS)) {
						hints = true;
						break;
					}
				}
				long maxGap = hints ? MAX_GAP_HINTS : MAX_GAP_TUTOR;
				for (AttemptAction action : attempt) {
					float perc = (float) i / attempt.size();
					long gap = action.timestamp.getTime() - last;
					if (perc > 0.65 && gap > maxGap) {
						cutGap = gap / 1000.0 / 60;
						break;
					}
					last = action.timestamp.getTime();
					lastRow = action.id;
					if (action.snapshot != null) lastSnapshotID = action.id;
					i++;
				}
				writer.printf("%s,%s,%d\n", attempt.id, assignment.name, lastRow);
				if (cutGap == 0) continue;
				writer.printf("%d%% (%.2fm): http://%s?id=%s&assignment=%s#%d\n",
						i * 100 / attempt.size(), cutGap,
						URL, attempt.id, assignment.name, lastSnapshotID);
			}
			writer.close();
		}
	}
}
