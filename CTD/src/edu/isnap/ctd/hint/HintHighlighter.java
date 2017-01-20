package edu.isnap.ctd.hint;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.Cast;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.ProgressDistanceMeasure;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;

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

//		node = node.copy(false);

		final BiMap<Node, Node> mapping = findSolutionMapping(node);

		final IdentityHashMap<Node, Highlight> colors = new IdentityHashMap<>();

		final HintConfig config = hintMap.config;

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
					Highlight highlight = parentMatches ? Highlight.Good : Highlight.Move;
					colors.put(node, highlight);
					if (highlight == Highlight.Move) {
						// For those without a matching parent, we need to insert them where
						// they belong
						Node moveParent = mapping.getTo(pair.parent);
						if (moveParent != null) {
							int insertIndex = 0;
							// Look back through your pair's earlier siblings...
							for (int i = pair.index() - 1; i >= 0; i--) {
								Node sibling = pair.parent.children.get(i);
								// If you can find one with a pair in the moveParent
								Node siblingPair = mapping.getTo(sibling);
								if (siblingPair != null && siblingPair.parent == moveParent) {
									// Set the insert index to right after it
									insertIndex = siblingPair.index() + 1;
									break;
								}
							}

							Insertion insertion = new Insertion(moveParent, node.type(),
									insertIndex);
							insertion.candidate = node;
							// If this is a function call parent, inserting the node should replace
							// the current node at this index
							if (isFunctionCall(moveParent) &&
									insertIndex < moveParent.children.size()) {
								insertion.replacement = moveParent.children.get(insertIndex);
							}
							edits.add(insertion);
						}
					}
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
					// Argument nodes are easy, since their order should be the same as their pair
					if (node.parent != null && node.parent == mapping.getTo(pair.parent) &&
							isFunctionCall(node.parent)) {
						if (node.index() != pair.index()) {
							colors.put(node, Highlight.Order);
							edits.add(new Reorder(node, pair.index()));
						}
						return;
					}

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
								System.err.println("two: " + childPair);
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
								if (preceder != null) {
									index = preceder.index() + 1;
									break;
								}
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
							if (originalChild.parent == node) {
								insertIndex = originalChild.index() + 1;
							}
						} else {
							// Otherwise we add an insertion
							Insertion insertion = new Insertion(node, child.type(), insertIndex);
							insertions.add(insertion);
							// If the node is being inserted in a function call then it replaces
							// whatever already exists at this index in the parent
							if (isFunctionCall(node) && insertIndex < node.children.size()) {
								insertion.replacement = node.children.get(insertIndex);
							}
							// If this is a non-script, we treat this new insertion as a "match"
							// and increment the insert index if possible
							if (!node.hasType(config.script)) {
								// We do have to make sure not to go out of bounds, though
								insertIndex = Math.min(node.children.size(), insertIndex + 1);
							}
						}
					}
				}
			}
		});
		edits.addAll(insertions);

		handleInsertionsAndMoves(colors, insertions, edits, config);

		// Remove excess deletions, whose parents are also deleted or moved
		// Note: we wait until the end in case they turn into Moves
		for (int i = 0; i < edits.size(); i++) {
			Deletion deletion = Cast.cast(edits.get(i), Deletion.class);
			if (deletion != null) {
				Highlight highlight = colors.get(deletion.parent);
				if (highlight == Highlight.Delete || highlight == Highlight.Move) {
					edits.remove(i--);
					continue;
				}

				// Also remove any deletions that are implicit in an inertion's replacement
				for (Insertion insertion : insertions) {
					if (insertion.replacement == deletion.node) {
						edits.remove(i--);
						break;
					}
				}
			}
		}

//		printHighlight(node, colors);

		Collections.sort(edits);

		return edits;
	}

	private final boolean isFunctionCall(Node node) {
		// TODO: Figure out a more precise way of determining this
		HintConfig config = hintMap.config;
		return !node.hasType(config.script) && !config.haveSideScripts.contains(node.type());
	}

	private BiMap<Node, Node> findSolutionMapping(Node node) {
		HintConfig config = hintMap.getHintConfig();
		ProgressDistanceMeasure dm = new ProgressDistanceMeasure(
				config.progressOrderFactor, 1, 0.25, config.script, config.literal);
		List<Node> solutions = preprocessSolutions(hintMap);
		Node bestMatch = NodeAlignment.findBestMatch(node, solutions, dm);
		if (bestMatch == null) throw new RuntimeException("No matches!");

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

		System.out.println("------------------------------");
		System.out.println(bestMatch.prettyPrint(labels));
		return mapping;
	}


	/**
	 * Remove side scripts from the submitted solutions. We do this to prevent side-script matches
	 * from have too much influence in the matching process.
	 */
	private List<Node> preprocessSolutions(HintMap hintMap) {
		final HintConfig config = hintMap.config;

		// First figure out how many scripts each solution has at each node
		final ListMap<Node, Integer> scriptCounts = new ListMap<>();
		for (Node node : hintMap.solutions) {
			node.recurse(new Action() {
				@Override
				public void run(Node node) {
					if (!config.haveSideScripts.contains(node.type())) return;
					int scripts = 0;
					for (Node child : node.children) {
						if (child.hasType(config.script)) {
							scripts++;
						}
					}
					scriptCounts.add(HintMap.toRootPath(node).root(), scripts);
				}
			});
		}

		// Then calculate the median number of scripts for all solutions
		final CountMap<Node> scriptMedians = new CountMap<>();
		for (Node node : scriptCounts.keySet()) {
			List<Integer> counts = scriptCounts.get(node);
			Collections.sort(counts);
			int median = counts.get(counts.size() / 2);
			scriptMedians.put(node, median);
		}

		// Then remove the smallest scripts from solutions which have more than the median count
		List<Node> solutions = new LinkedList<>();
		for (Node node : hintMap.solutions) {
			Node copy = node.copy(false);
			copy.recurse(new Action() {
				@Override
				public void run(Node node) {
					if (!config.haveSideScripts.contains(node.type())) return;
					int median = scriptMedians.getCount(HintMap.toRootPath(node).root());
					List<Integer> sizes = new LinkedList<>();
					for (Node child : node.children) {
						if (child.hasType(config.script)) {
							sizes.add(child.size());
						}
					}
					if (sizes.size() <= median) return;
					Collections.sort(sizes);
					int minSize = median == 0 ? Integer.MAX_VALUE :
						sizes.get(sizes.size() - median);
					for (int i = 0; i < node.children.size(); i++) {
						Node child = node.children.get(i);
						if (child.hasType(config.script) && child.size() < minSize) {
//							System.out.println("Preprocess removed: " + node.children.get(i));
							node.children.remove(i--);
						}
					}
				}
			});
			solutions.add(copy);
		}

		return solutions;
	}

	private void handleInsertionsAndMoves(final IdentityHashMap<Node, Highlight> colors,
			final List<Insertion> insertions, List<EditHint> edits, HintConfig config) {
		List<EditHint> toRemove = new LinkedList<>();
		List<EditHint> toAdd = new LinkedList<>();

		// For each deleted node, see if it should be inserted, and if so change it to a root move
		for (EditHint edit : edits) {
			if (!(edit instanceof Deletion)) continue;
			Deletion deletion = (Deletion) edit;
			Node deleted = deletion.node;

			if (colors.get(deleted) != Highlight.Delete) continue;
			for (Insertion insertion : insertions) {
				boolean matchParent = deleted.parent == insertion.parent;
				boolean sameType = deleted.hasType(insertion.type);

				// If it's an insertion/deletion of the same type in the same script parent, it's
				// just a reorder
				if (sameType && matchParent && insertion.parent.hasType(config.script)) {
					colors.put(deleted, Highlight.Move);
					toAdd.add(new Reorder(deleted, insertion.index));
					toRemove.add(deletion);
					toRemove.add(insertion);
					// Set the insertion candidate, so it's not somehow used later in the loop
					insertion.candidate = deleted;
					break;
				}

				// Moves are deletions that could be instead moved to perform a needed insertion
				// TODO: find the best match, not just the first
				if (insertion.candidate == null && sameType &&
						!insertion.type.equals(config.literal)) {
					colors.put(deleted, Highlight.Move);
					insertion.candidate = deleted;
					toRemove.add(deletion);
					break;
				}
			}
		}

		edits.removeAll(toRemove);
		edits.addAll(toAdd);
	}

	@SuppressWarnings("unused")
	private void printHighlight(Node node, final IdentityHashMap<Node, Highlight> colors) {
		IdentityHashMap<Node, String> prefixMap = new IdentityHashMap<>();
		for (Entry<Node, Highlight> entry : colors.entrySet()) {
			prefixMap.put(entry.getKey(), entry.getValue().name().substring(0, 1));
		}
		System.out.println(node.prettyPrint(prefixMap));
	}

	public static abstract class EditHint implements Hint, Comparable<EditHint> {
		protected abstract void editChildren(List<String> children);
		protected abstract String action();
		protected abstract double priority();

		public final Node parent;

		public EditHint(Node parent) {
			this.parent = parent;
		}

		@Override
		public String type() {
			return "highlight";
		}

		@Override
		public JSONObject data() {
			JSONObject data = new JSONObject();
			data.put("parent", Node.getNodeReference(parent));
			data.put("action", action());
			return data;
		}

		@Override
		public String from() {
			LinkedList<String> items = new LinkedList<>(Arrays.asList(parent.getChildArray()));
			return action() + ": " + rootString(parent) + ": " + items;
		}

		@Override
		public String to() {
			LinkedList<String> items = new LinkedList<>(Arrays.asList(parent.getChildArray()));
			editChildren(items);
			return action() + ": " + rootString(parent) + ": " + items;
		}

		protected String getID(Node node) {
			return "";
		}

		protected String rootString(Node node) {
			return HintMap.toRootPath(node).root().toString();
		}

		@Override
		public String toString() {
			LinkedList<String> items = new LinkedList<>(Arrays.asList(parent.getChildArray()));
			String from = items.toString();
			editChildren(items);
			String to = items.toString();
			return rootString(parent) + ": " + from + " -> " + to;
		}

		@Override
		public int compareTo(EditHint o) {
			return Double.compare(o.priority(), priority());
		}
	}

	private static class Insertion extends EditHint {
		public final String type;
		public final int index;

		/** The node the inserted node should replace (in the same location) */
		public Node replacement;
		/** A candidate node elsewhere that could be used to do the replacement */
		public Node candidate;

		@Override
		public String action() {
			return "insert";
		}

		public Insertion(Node parent, String type, int index) {
			super(parent);
			this.type = type;
			this.index = index;
			if (index > parent.children.size()) {
				throw new RuntimeException("Insert index out of range");
			}
		}

		@Override
		public JSONObject data() {
			JSONObject data = super.data();
			data.put("index", index);
			data.put("type", type);
			data.put("replacement", Node.getNodeReference(replacement));
			data.put("candidate", Node.getNodeReference(candidate));
			LinkedList<String> items = new LinkedList<>(Arrays.asList(parent.getChildArray()));
			data.put("from", toJSONArray(items));
			editChildren(items);
			data.put("to", toJSONArray(items));
			return data;
		}

		private JSONArray toJSONArray(LinkedList<String> items) {
			JSONArray array = new JSONArray();
			for (String item : items) {
				array.put(item);
			}
			return array;
		}

		@Override
		protected void editChildren(List<String> children) {
			if (replacement != null) {
				children.remove(index);
			}
			children.add(index, type);
		}

		@Override
		public String from() {
			String text = super.from();
			if (candidate != null) {
				text += " using " + rootString(candidate);
			}
			return text;
		}

		@Override
		public String to() {
			String text = super.to();
			if (candidate != null) {
				text += " using " + rootString(candidate);
			}
			return text;
		}

		@Override
		protected double priority() {
			return 5 + (candidate == null ? 0  : 1);
		}
	}

	public static class Deletion extends EditHint {
		public final Node node;

		@Override
		protected String action() {
			return "delete";
		}

		public Deletion(Node node) {
			super(node.parent);
			this.node = node;
		}

		@Override
		public JSONObject data() {
			JSONObject data = super.data();
			data.put("node", Node.getNodeReference(node));
			return data;
		}

		@Override
		protected void editChildren(List<String> children) {
			children.remove(node.index());
		}

		@Override
		protected double priority() {
			return 1;
		}
	}

	public static class Reorder extends EditHint {
		public final Node node;
		public final int index;

		@Override
		protected String action() {
			return "reorder";
		}

		public Reorder(Node node, int index) {
			super(node.parent);
			this.node = node;
			this.index = index;
		}

		@Override
		public JSONObject data() {
			JSONObject data = super.data();
			data.put("node", Node.getNodeReference(node));
			data.put("index", index);
			return data;
		}

		@Override
		protected void editChildren(List<String> children) {
			int rIndex = node.index();
			int aIndex = index;
			if (rIndex < aIndex) aIndex--;
			children.add(aIndex, children.remove(rIndex));
		}

		@Override
		protected double priority() {
			return 3;
		}
	}

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}
}
