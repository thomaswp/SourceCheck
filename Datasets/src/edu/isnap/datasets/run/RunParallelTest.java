package edu.isnap.datasets.run;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Spring2016;
import edu.isnap.parser.Store.Mode;

public class RunParallelTest {


	public static void main(String[] args) {

		Assignment assignment = Spring2016.GuessingGame1;

//		assignment.clean();
		long start = System.currentTimeMillis();
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Ignore, false);
		long load = System.currentTimeMillis() - start;

		List<Integer> hashes = new LinkedList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			hashes.add(attempt.id.hashCode());
			for (AttemptAction action : attempt) {
				hashes.add(action.message.hashCode());
				hashes.add(action.data.hashCode());
				if (action.snapshot != null) {
					hashes.add(action.snapshot.toCode().hashCode());
				}
			}
		}
		System.out.println("Load Time: " + load);
		System.out.println(hashes.hashCode());
	}
}
