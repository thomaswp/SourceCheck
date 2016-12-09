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
		Good, Add, Delete, Move, RootMove
	}

	private final HintMap hintMap;

	public HintHighlighter(HintMap hintMap) {
		this.hintMap = hintMap;
	}

	public void highlight(Node node) {
		node = node.copy(false);

		final IdentityHashMap<Node, Highlight> colors = new IdentityHashMap<>();
		final List<Insertion> insertions = new LinkedList<>();

		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				highlight(node, colors, insertions);
			}
		});

		// For each deleted node, see if it should be inserted, and if so change it to a root move
		for (Node deleted : colors.keySet()) {
			if (colors.get(deleted) != Highlight.Delete) continue;
			for (Insertion insertion : insertions) {
				if (deleted.hasType(insertion.type)) {
					colors.put(deleted, Highlight.RootMove);
					// TODO: find the best match, not just the first
					break;
				}
			}
		}

		for (Insertion insertion : insertions) {
			colors.put(insertion.insert(), Highlight.Add);
		}

		IdentityHashMap<Node, String> prefixMap = new IdentityHashMap<>();
		for (Entry<Node, Highlight> entry : colors.entrySet()) {
			prefixMap.put(entry.getKey(), entry.getValue().name().substring(0, 1));
		}

		System.out.println(node.prettyPrint(prefixMap));
	}

	private void highlight(Node node, Map<Node, Highlight> colors, List<Insertion> insertions) {
		if (node.hasType("script")) {
			System.out.println();
		}

		// Get the goal state a hint would point us to
		VectorState goalState = HintGenerator.getGoalState(hintMap, node);
		if (goalState == null) return;
		String[] children = node.getChildArray();

		// Copy the children to a mutable list
		List<String> sequence = new LinkedList<>(Arrays.asList(children));
		// We're going to move nodes, so keep a map from sequence-index to original children-index
		HashMap<Integer, Integer> indexMap = new HashMap<>();
		// Perform any move edits and take an array snapshot
		Alignment.doEdits(sequence, goalState.items, Alignment.MoveEditor);
		String[] moved = toArray(sequence);
		// Compare the original children and the moved children and get a mapping
		List<int[]> pairs = Alignment.alignPairs(children, moved, 1, 1, 100);
		for (int[] pair : pairs) {
			if (pair[0] >= 0 && pair[1] < 0) {
				// Any unpaired child was moved, so highlight it
				colors.put(node.children.get(pair[0]), Highlight.Move);
			} else if (pair[0] >= 0 && pair[1] >= 0) {
				// The others were kept in order, so add them to the map
				indexMap.put(pair[1], pair[0]);
			}
		}

		// Do the same thing with deletion
		Alignment.doEdits(sequence, goalState.items, Alignment.DeleteEditor);
		String[] deleted = toArray(sequence);
		pairs = Alignment.alignPairs(moved, deleted, 1, 1, 100);
		for (int[] pair : pairs) {
			if (pair[0] >= 0 && indexMap.containsKey(pair[0])) {
				// If the deleted version doen't have the node, mark it; otherwise, it's good
				Highlight highlight = pair[1] >= 0 ? Highlight.Good : Highlight.Delete;
				colors.put(node.children.get(indexMap.get(pair[0])), highlight);
			}
		}

		// Now do something similar with additions
		Alignment.doEdits(sequence, goalState.items, Alignment.AddEditor);
		String[] added = toArray(sequence);
		pairs = Alignment.alignPairs(moved, added, 1, 1, 100);
		// Turn the pairs into a added-index/moved-index mapping
		HashMap<Integer, Integer> pairMap = new HashMap<>();
		for (int[] pair : pairs) {
			pairMap.put(pair[1], pair[0]);
		}
		// Keep track of the index at which to insert the added nodes
		int insertIndex = 0;
		for (int i = 0; i < added.length; i++) {
			// Get the moved-index of each node in the goal
			int originalIndex = pairMap.get(i);
			if (originalIndex == -1) {
				// If it doesn't exist, add it at the current index
				insertions.add(new Insertion(node, added[i], insertIndex));
			} else {
				// Otherwise update the index
				insertIndex = originalIndex + 1;
			}
		}

	}

	private static class Insertion {
		public final Node parent;
		public final String type;
		public final int index;

		public Insertion(Node parent, String type, int index) {
			this.parent = parent;
			this.type = type;
			this.index = index;
		}

		public Node insert() {
			Node child = new Node(parent, type);
			parent.children.add(index, child);
			return child;
		}
	}

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}
}
