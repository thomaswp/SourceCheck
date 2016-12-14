package edu.isnap.ctd.hint;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.ProgressDistanceMeasure;
import edu.isnap.ctd.util.map.BiMap;

public class HintHighlighter {

	public static enum Highlight {
		Good, Add, Delete, Order, Move
	}

	private final HintMap hintMap;

	public HintHighlighter(HintMap hintMap) {
		this.hintMap = hintMap;
	}

	public List<EditHint> highlight(Node node) {

		final List<EditHint> edits = new LinkedList<>();

		node = node.copy(false);

		final BiMap<Node, Node> mapping = findSolutionMapping(node);

		final IdentityHashMap<Node, Highlight> colors = new IdentityHashMap<>();

		// First simply ID the nodes that have a pair with the same parent (Good), a different
		// parent (Move) and those that don't (Delete)
		// Note that the Good label is not final, as Good nodes may need to be reordered (Order)
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				Node pair = mapping.getFrom(node);
				if (pair != null) {
					Node parent = node.parent;
					boolean parentMatches = parent == null ||
							mapping.getFrom(parent) == pair.parent;
					colors.put(node, parentMatches ? Highlight.Good : Highlight.Move);
				} else {
					colors.put(node, Highlight.Delete);
					edits.add(new Deletion(node));
				}
			}
		});

		// Find nodes that need to be reordered (Order)
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				// Start by finding parent nodes that have a pair
				Node pair = mapping.getFrom(node);
				if (pair != null) {
					// Instead of aligning the node types, which might repeat, we align the original
					// node indices (1, 2, 3...) with the order those node-indices appear in the
					// paired parent's children

					// Maps pair children to the index of their corresponding original children
					// We use string, since the alignment algorithm will expect them
					IdentityHashMap<Node, String> pairOrders = new IdentityHashMap<>();
					// Get the original good nodes in the order they appear
					List<Node> fromChildren = new LinkedList<>();
					// And create the lists that will hold the indices to compare
					List<String> fromItems = new LinkedList<>(), toItems = new LinkedList<>();
					// We use a tree map to order the pair children by their index in their parent
					TreeMap<Integer, Node> toChildren = new TreeMap<>();

					for (Node child : node.children) {
						// We only work with children that have a pair
						if (colors.get(child) == Highlight.Good) {
							// The from items are the original indices (1, 2, 3..)
							String index = String.valueOf(fromChildren.size());
							fromItems.add(index);
							fromChildren.add(child);

							// Get the child pair and add it in order of its index
							Node childPair = mapping.getFrom(child);
							pairOrders.put(childPair, index);
							Node x = toChildren.put(childPair.index(), childPair);
							if (x != null) {
								// Check for multiple nodes mapped to a single one
								System.out.println("two: " + childPair);
							}
						}
					}
					// Get the original child's index corresponding to each pair
					for (Node pairChild : toChildren.values()) {
						toItems.add(pairOrders.get(pairChild));
					}

					// Now we have two lists, e.g. (1, 2, 3) and (3, 1, 2)
					// so we align them and see which ones don't end up with partners
					List<int[]> alignPairs = Alignment.alignPairs(
							toArray(fromItems), toArray(toItems), 1, 1, 100);
					for (int[] ap : alignPairs) {
						if (ap[0] >= 0 && ap[1] < 0) {
							// Unpaired elements get marked for reordering
							Node toReorder = fromChildren.get(ap[0]);
							colors.put(toReorder, Highlight.Order);

							// Find the best index for the Node
							Node np = mapping.getFrom(toReorder);
							// Work backwards from the reordered node's pair and find a preceding
							// pair-node that is mapped to: it goes after that. If none is found,
							// we insert at the beginning
							int index = 0;
							for (int i = np.index() - 1; i >= 0; i--) {
								Node preceder = mapping.getTo(np.parent.children.get(i));
								if (preceder != null) index = preceder.index() + 1;
							}
							edits.add(new Reorder(toReorder, index));
						}
					}
				}
			}
		});

		// Now find needed insertions
		final List<Insertion> insertions = new LinkedList<>();
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				// Look for parents with pairs
				Node pair = mapping.getFrom(node);
				if (pair != null) {
					// For those we iterate through their parent-pair's children
					int insertIndex = 0;
					for (Node child : pair.children) {
						Node originalChild = mapping.getTo(child);
						if (originalChild != null) {
							// For paired children, we update the insertion index
							insertIndex = originalChild.index() + 1;
						} else {
							// Otherwise we add an insertion
							insertions.add(new Insertion(node, child.type(), insertIndex));
						}
					}
				}
			}
		});
		edits.addAll(insertions);

		handleInsertionsAndMoves(colors, insertions, edits);

		printHighlight(node, colors);

		return edits;
	}

	private BiMap<Node, Node> findSolutionMapping(Node node) {
		HintConfig config = hintMap.getHintConfig();
		ProgressDistanceMeasure dm = new ProgressDistanceMeasure(
				config.progressOrderFactor, 1, 0.25, config.script);
		Node bestMatch = NodeAlignment.findBestMatch(node, hintMap.solutions, dm);

		NodeAlignment alignment = new NodeAlignment(node, bestMatch);
		alignment.calculateCost(dm, true);

		final IdentityHashMap<Node, String> labels = new IdentityHashMap<>();
		final BiMap<Node, Node> mapping = alignment.mapping;
		bestMatch.recurse(new Action() {
			@Override
			public void run(Node node) {
				Node pair = mapping.getTo(node);
				if (pair != null) {
					labels.put(node, pair.id);
				}
			}
		});

//		System.out.println("------------------------------");
//		System.out.println(bestMatch.prettyPrint(labels));
		return mapping;
	}


	public void ctdHighlight(Node node) {
		node = node.copy(false);

		final IdentityHashMap<Node, Highlight> colors = new IdentityHashMap<>();
		final List<Insertion> insertions = new LinkedList<>();

		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				highlight(node, colors, insertions);
			}
		});

		handleInsertionsAndMoves(colors, insertions, null);

		printHighlight(node, colors);
	}

	private void handleInsertionsAndMoves(final IdentityHashMap<Node, Highlight> colors,
			final List<Insertion> insertions, List<EditHint> edits) {
		List<EditHint> toRemove = new LinkedList<>();

		// For each deleted node, see if it should be inserted, and if so change it to a root move
		for (EditHint edit : edits) {
			if (!(edit instanceof Deletion)) continue;
			Deletion deletion = (Deletion) edit;
			Node deleted = deletion.node;

			if (colors.get(deleted) != Highlight.Delete) continue;
			for (Insertion insertion : insertions) {
				if (insertion.candidate == null && deleted.hasType(insertion.type)) {
					colors.put(deleted, Highlight.Move);
					// TODO: find the best match, not just the first
					insertion.candidate = deleted;
					toRemove.add(deletion);
					break;
				}
			}
		}

		edits.removeAll(toRemove);

		Collections.reverse(insertions);
		System.out.println("Insertions: " + insertions);
		for (Insertion insertion : insertions) {
			colors.put(insertion.insert(), Highlight.Add);
		}
	}

	private void printHighlight(Node node, final IdentityHashMap<Node, Highlight> colors) {
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
				colors.put(node.children.get(pair[0]), Highlight.Order);
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

	public static abstract class EditHint implements Hint {

		protected abstract String description();
		protected abstract JSONObject getData();

		@Override
		public String from() {
			return description();
		}

		@Override
		public String to() {
			return description();
		}

		@Override
		public String type() {
			return "highlight";
		}

		@Override
		public String data() {
			return getData().toString();
		}

		protected String getID(Node node) {
			return "";
		}
	}

	private static class Insertion extends EditHint {
		public final Node parent;
		public final String type;
		public final int index;

		public Node candidate;

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

		@Override
		public String toString() {
			return Arrays.toString(parent.getChildArray()) + ": " + index + "/" + type;
		}

		@Override
		protected String description() {
			return String.format("Insert %s at index %d of parent %s%s", type, index, parent,
					candidate == null ? "" : (" using " + candidate));
		}

		@Override
		protected JSONObject getData() {
			JSONObject obj = new JSONObject();
			return obj;
		}
	}

	public static class Deletion extends EditHint {
		public final Node node;

		public Deletion(Node node) {
			this.node = node;
		}

		@Override
		protected String description() {
			return "Delete: " + node;
		}

		@Override
		protected JSONObject getData() {
			JSONObject obj = new JSONObject();
			return obj;
		}
	}

	public static class Reorder extends EditHint {
		public final Node node;
		public final int index;

		public Reorder(Node node, int index) {
			this.node = node;
			this.index = index;
		}

		@Override
		protected String description() {
			return String.format("Move from %d to %d: %s", node.index(), index, node);
		}

		@Override
		protected JSONObject getData() {
			JSONObject obj = new JSONObject();
			return obj;
		}
	}

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}
}
