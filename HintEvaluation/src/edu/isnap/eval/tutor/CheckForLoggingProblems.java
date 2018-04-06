package edu.isnap.eval.tutor;

import java.util.HashSet;
import java.util.Map;

import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2017;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

public class CheckForLoggingProblems {

	private final static int TIME_GAP = 20 * 1000;
	private final static int SIZE_GAP = 5;

	public static void main(String[] args) {
		Map<String, AssignmentAttempt> attempts = Fall2017.GuessingGame1.load(Mode.Use, true);

		HashSet<String> ignoreActions = new HashSet<>();
		ignoreActions.add(AttemptAction.BLOCK_DUPLICATE_ALL);
		ignoreActions.add(AttemptAction.BLOCK_DUPLICATE_BLOCK);
		ignoreActions.add(AttemptAction.BLOCK_EDITOR_APPLY);
		ignoreActions.add(AttemptAction.BLOCK_EDITOR_START);

		for (AssignmentAttempt attempt : attempts.values()) {
			int lastSize = 0;
			long lastTime = 0;
			HashSet<String> blockIDs = new HashSet<>();
			boolean ignore = false;
			for (AttemptAction action : attempt) {
				long time = action.timestamp.getTime();

				SimpleNodeBuilder.toTree(action.snapshot, true)
				.recurse(node -> blockIDs.add(node.id));

				int size = blockIDs.size();

				if (ignoreActions.contains(action.message)) ignore = true;

				if (lastTime > 0 &&
						Math.abs(time - lastTime) >= TIME_GAP &&
						Math.abs(size - lastSize) >= SIZE_GAP) {
					if (ignore) {
						ignore = false;
					} else {
						System.out.printf("%s %d: dTime: %02.02f, dSize: %03d\n",
								attempt.id, action.id, (time - lastTime) / 60000.0,
								size - lastSize);
					}
				}
				lastTime = time;
				lastSize = size;
			}
		}
	}
}
