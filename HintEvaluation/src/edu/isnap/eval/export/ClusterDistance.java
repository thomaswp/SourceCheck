package edu.isnap.eval.export;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.KMedoids.DistanceMeasure;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

public class ClusterDistance {

	private static void write(String path, List<Assignment> assignments,
			DistanceMeasure<Node> dm, boolean fullPath) {

		Map<String, Node> nodes = new HashMap<>();

		for (Assignment assignment : assignments) {
			Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, true);
			for (AssignmentAttempt attempt : attempts.values()) {
				if (!attempt.isLikelySubmitted()) continue;
				for (AttemptAction action : attempt) {
					Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
					nodes.put("", node);
				}
			}
		}
	}
}
