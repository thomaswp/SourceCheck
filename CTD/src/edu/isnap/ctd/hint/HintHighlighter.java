package edu.isnap.ctd.hint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.util.Alignment;

public class HintHighlighter {

	public static enum Highlight {
		Good, Add, Delete, Move, MoveRoots
	}

	private final HintMap hintMap;

	public HintHighlighter(HintMap hintMap) {
		this.hintMap = hintMap;
	}

	public void highlight(Node node) {
		final IdentityHashMap<Node, Highlight> colors = new IdentityHashMap<>();

		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				highlight(node, colors);
			}
		});

		IdentityHashMap<Node, String> prefixMap = new IdentityHashMap<>();
		for (Entry<Node, Highlight> entry : colors.entrySet()) {
			prefixMap.put(entry.getKey(), entry.getValue().name().substring(0, 1));
		}

		System.out.println(prefixMap);
		System.out.println(node.prettyPrint(prefixMap));
	}

	private void highlight(Node node, Map<Node, Highlight> colors) {
		if (node.hasType("script")) {
			System.out.println();
		}

		VectorState goalState = HintGenerator.getGoalState(hintMap, node);
		if (goalState == null) return;
		String[] children = node.getChildArray();

		HashMap<Integer, Integer> indexMap = new HashMap<>();

		List<String> sequence = new LinkedList<>(Arrays.asList(children));
		Alignment.doEdits(sequence, goalState.items, Alignment.MoveEditor);
		String[] moved = toArray(sequence);
		List<int[]> pairs = Alignment.alignPairs(children, moved, 1, 1, 100);
		for (int[] pair : pairs) {
			if (pair[0] >= 0 && pair[1] < 0) {
				colors.put(node.children.get(pair[0]), Highlight.Move);
			} else if (pair[0] >= 0 && pair[1] >= 0) {
				indexMap.put(pair[1], pair[0]);
			}
		}

		Alignment.doEdits(sequence, goalState.items, Alignment.DeleteEditor);
		String[] deleted = toArray(sequence);
		pairs = Alignment.alignPairs(moved, deleted, 1, 1, 100);
		for (int[] pair : pairs) {
			if (pair[0] >= 0 && indexMap.containsKey(pair[0])) {
				Highlight highlight = pair[1] >= 0 ? Highlight.Good : Highlight.Delete;
				colors.put(node.children.get(indexMap.get(pair[0])), highlight);
			}
		}

	}

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}
}
