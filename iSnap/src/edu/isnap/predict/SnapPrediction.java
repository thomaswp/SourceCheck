package edu.isnap.predict;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import difflib.StringUtills;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class SnapPrediction {

	public static void predict(Assignment... assignments) {

		final ListMap<String, String> components = new ListMap<>();

		List<AssignmentAttempt> attempts = new LinkedList<>();
		for (Assignment assignment : assignments) {
			attempts.addAll(assignment.load(Mode.Use, true, true, new SnapParser.SubmittedOnly())
					.values());
		}
		for (AssignmentAttempt attempt : attempts) {
			Snapshot submission = attempt.submittedSnapshot;
			Node node = SimpleNodeBuilder.toTree(submission, true);
			final String id = attempt.id;

			node.recurse(new Action() {
				@Override
				public void run(Node node) {
					Node root = HintMap.toRootPath(node).root();
					VectorState state = new VectorState(node.getChildArray());
//					VectorState goal = generator.getGoalState(node);

					String key = getKey(root, state);
					components.add(key, id);
				}

				private String getKey(Node root, VectorState state) {
					String key = getKey(root);
					if (state.items.length > 0) {
						key += ":" + StringUtills.join(Arrays.asList(state.items), "+");
					}
					return key;
				}

				private String getKey(Node root) {
					String key = root.type();
					while (!root.children.isEmpty()) {
						root = root.children.get(0);
						key += "." + root.type();
					}
					return key;
				}
			});
		}

		Spreadsheet spreadsheet = new Spreadsheet();
		List<String> keys = new ArrayList<>(components.keySet());
		for (int i = 0; i < keys.size(); i++) {
			if (components.get(keys.get(i)).size() <= 2) {
				keys.remove(i--);
			}
		}
		Collections.sort(keys, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return -Integer.compare(components.get(o1).size(),
						components.get(o2).size());
			}
		});
		for (AssignmentAttempt attempt : attempts) {
			spreadsheet.newRow();
//			spreadsheet.put("id", attempt.id);
//			spreadsheet.put("grade", attempt.grade.average());
//			spreadsheet.put("time", attempt.totalActiveTime);
			for (Entry<String, Integer> entry : attempt.grade.tests.entrySet()) {
				spreadsheet.put(entry.getKey().replace(" ", "_"), entry.getValue() == 2);
			}
			for (String key : keys) {
				spreadsheet.put(key, components.get(key).contains(attempt.id));
			}
		}

		try {
			spreadsheet.write(assignments[0].analysisDir() + "/pred.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
