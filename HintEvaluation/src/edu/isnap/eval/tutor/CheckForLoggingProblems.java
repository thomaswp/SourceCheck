package edu.isnap.eval.tutor;

import java.util.HashSet;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.run.RunParallelTest;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

@SuppressWarnings("unused")
public class CheckForLoggingProblems {

	private final static int TIME_GAP = 20 * 1000;
	private final static int SIZE_GAP = 5;

	public static void main(String[] args) {
		checkForAssignmentSwitching(Fall2017.Squiral);
//		checkForUnstableLogs(Fall2017.Squiral);
//		getSummaryHashes(Spring2017.instance);
	}

	private static void getSummaryHashes(Dataset dataset) {
		for (Assignment assignment : dataset.all()) {
			int hash = RunParallelTest.getSummaryHash(assignment.load(Mode.Use, false));
			System.out.println(assignment.name + ": " + hash);
		}
	}

	private static void checkForAssignmentSwitching(Assignment assignment) {
		Map<String, AssignmentAttempt> attempts = assignment.loadSubmitted(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			HashSet<String> blockIDs = new HashSet<>();
			int lastSize = -1;
			long lastTime = -1;
			String switchFrom = null;
			for (AttemptAction action : attempt) {
				if (AttemptAction.ASSIGNMENT_SET_ID_FROM.equals(action.message)) {
					switchFrom = action.data;
				}

				long time = action.timestamp.getTime();
				if (action.snapshot != null) {
					SimpleNodeBuilder.toTree(action.snapshot, true)
					.recurse(node -> blockIDs.add(node.id));

					int size = blockIDs.size();
					if (switchFrom != null) {
						System.out.printf("%s %d: dTime: %02.02f, dSize: %02d, pTime: %.02f (%s)\n",
								attempt.id, action.id,
								(time - lastTime) / 60000.0, size - lastSize,
								(double) action.currentActiveTime / attempt.totalActiveTime,
								switchFrom);
						switchFrom = null;
					}
					lastSize = size;
				}
				lastTime = time;
			}
		}
	}

	private static void checkForUnstableLogs(Assignment assignment) {
		Map<String, AssignmentAttempt> attempts = assignment.loadSubmitted(Mode.Use, false);

		HashSet<String> ignoreActions = new HashSet<>();
		// These actions can create a large jump when a custom block
		ignoreActions.add(AttemptAction.BLOCK_DUPLICATE_ALL);
		ignoreActions.add(AttemptAction.BLOCK_DUPLICATE_BLOCK);
		ignoreActions.add(AttemptAction.BLOCK_EDITOR_APPLY);
		ignoreActions.add(AttemptAction.BLOCK_EDITOR_START);
		ignoreActions.add(AttemptAction.BLOCK_EDITOR_CANCEL);

		for (AssignmentAttempt attempt : attempts.values()) {
			int lastSize = -1;
			long lastTime = -1;
			HashSet<String> blockIDs = new HashSet<>();
			boolean ignore = false;
			for (AttemptAction action : attempt) {
				long time = action.timestamp.getTime();

				if (ignoreActions.contains(action.message)) ignore = true;

				if (action.snapshot != null) {
					SimpleNodeBuilder.toTree(action.snapshot, true)
					.recurse(node -> blockIDs.add(node.id));

					int size = blockIDs.size();

					if (!ignore && lastSize >= 0 &&
							Math.abs(time - lastTime) >= TIME_GAP &&
							Math.abs(size - lastSize) >= SIZE_GAP) {
						System.out.printf("%s %d: dTime: %02.02f, dSize: %03d\n",
								attempt.id, action.id, (time - lastTime) / 60000.0,
								size - lastSize);
					}
					ignore = false;
					lastSize = size;
				}
				lastTime = time;
			}
		}
	}
}
