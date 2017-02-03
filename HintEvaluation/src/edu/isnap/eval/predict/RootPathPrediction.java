package edu.isnap.eval.predict;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import difflib.StringUtills;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.util.Spreadsheet;

public class RootPathPrediction extends SnapGradePrediction {

	public static void main(String[] args) {
		new RootPathPrediction().predict(Fall2016.Squiral);
	}

	private final ListMap<String, String> components = new ListMap<>();
	private final List<String> keys = new ArrayList<>(components.keySet());

	@Override
	protected String getName() {
		return "rootpath";
	}

	@Override
	public void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config) {
		for (AssignmentAttempt attempt : attemptMap.keySet()) {
			Node node = attemptMap.get(attempt);
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
	}

	@Override
	protected void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt) {
		for (String key : keys) {
			spreadsheet.put(key, components.get(key).contains(attempt.id));
		}
	}
}
