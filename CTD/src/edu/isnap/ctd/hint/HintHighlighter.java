package edu.isnap.ctd.hint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.Cast;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.DistanceMeasure;
import edu.isnap.ctd.util.NodeAlignment.ProgressDistanceMeasure;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;

public class HintHighlighter {

	public static enum Highlight {
		Good, Add, Delete, Order, Move, Replaced
	}

	private final List<Node> solutions;
	private final HintConfig config;

	public HintHighlighter(HintMap hintMap) {
		this(hintMap.solutions, hintMap.config);
	}

	public HintHighlighter(List<Node> solutions, HintConfig config) {
		this.solutions = preprocessSolutions(solutions, config);
		this.config = config;
	}

	public List<EditHint> highlight(Node node) {
		BiMap<Node, Node> mapping = findSolutionMapping(node);
		return highlight(node, mapping);
	}

	public List<EditHint> highlight(Node node, final BiMap<Node, Node> mapping) {
		final List<EditHint> edits = new ArrayList<>();
		final IdentityHashMap<Node, Highlight> colors = new IdentityHashMap<>();

		// First identify all unpaired nodes and mark them for deletion (though this can be
		// overridden if we can find a use for them)
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				Node pair = mapping.getFrom(node);
				if (pair == null) {
					colors.put(node, Highlight.Delete);
					edits.add(new Deletion(node));
				}
			}
		});

		// Then simply ID the nodes that have a pair with the same parent (Good), a different
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
						Node moveParent = mapping.getTo(pair.parent);
						if (moveParent == null) {
							// If the pair's parent has no original parent, we add a placeholder
							// insert with a missing parent so the node is marked for movement
							Node parentClone = pair.parent.copy();
							parentClone.children.remove(pair.index());
							Insertion insertion = new Insertion(
									parentClone, pair, pair.index(), true);
							insertion.candidate = node;
							edits.add(insertion);
						} else {
							// For those without a matching parent, we need to insert them where
							// they belong
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

							Insertion insertion = new Insertion(moveParent, pair, insertIndex);
							insertion.candidate = node;
							// If this is a code element parent, inserting the node should replace
							// the current node at this index
							if (isCodeElement(moveParent) &&
									insertIndex < moveParent.children.size()) {
								insertion.replacement = moveParent.children.get(insertIndex);
							}
							edits.add(insertion);
						}
					}
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
							isCodeElement(node.parent) &&
							// Make sure the two code elements have the same number of children
							node.parent.children.size() == pair.parent.treeSize()) {
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
								// Make sure they have the same parent so we know the index is valid
								if (preceder != null && preceder.parent == toReorder.parent) {
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

		// This time, iterate over the nodes in the pair
		Node nodeMatch = mapping.getFrom(node);
		nodeMatch.recurse(new Action() {
			@Override
			public void run(Node pair) {
				// See if the pair has a corresponding node in the student's tree
				Node node = mapping.getTo(pair);
				if (node != null) {
					// If so, we iterate through its children and add them
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
							Insertion insertion = new Insertion(node, child, insertIndex);
							edits.add(insertion);
							// If the node is being inserted in a code element then it replaces
							// whatever already exists at this index in the parent
							if (isCodeElement(node) && insertIndex < node.children.size()) {
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
				} else if (mapping.getTo(pair.parent) == null) {
					// If not, and the pair's parent has no corresponding node either, it won't
					// be added by the above code, so we need to add it. Unfortunately, there's
					// no where to add it to the student's code, since it's parent has no
					// corresponding node, so we use the pair-parent and mark it as a special case.
					// It's useful to list these insertions anyway, since it allows us to mark nodes
					// as Move instead of Delete when they're used in not-yet-added parents

					// This really only makes sense for code element
					if (!isCodeElement(pair)) return;

					// We clone the pair-parent and remove the pair, so the insert text is
					// descriptive
					Node parentClone = pair.parent.copy();
					parentClone.children.remove(pair.index());
					Insertion insertion = new Insertion(parentClone, pair, pair.index(), true);
					edits.add(insertion);
				}
			}
		});


		// Filter out insertions
		final List<Insertion> insertions = new LinkedList<>();
		for (EditHint hint : edits) {
			Insertion insert = Cast.cast(hint, Insertion.class);
			if (insert != null) insertions.add(insert);
		}

		handleInsertionsAndMoves(colors, insertions, edits, mapping);

		// If any insert into a script should contain the children of the existing node at that
		// index, we want to express that as a replacement
		for (int i = 0; i < edits.size(); i++) {
			Insertion insertion = Cast.cast(edits.get(i), Insertion.class);
			if (insertion != null) {
				addScriptReplacement(insertion, mapping, edits, colors);
			}
		}

		// Remove unneeded edits
		for (int i = 0; i < edits.size(); i++) {
			// Remove any inserts that have a missing parent and no candidate, as they won't show
			Insertion insertion = Cast.cast(edits.get(i), Insertion.class);
			if (insertion != null) {
				if (insertion.missingParent && insertion.candidate == null) {
					edits.remove(i--);
					continue;
				}
			}

			Deletion deletion = Cast.cast(edits.get(i), Deletion.class);
			if (deletion != null) {
				// Don't bother deleting certain function calls, which students may add as
				// personalization but don't harm the final product. May be assignment specific.
				if (config.harmlessCalls.contains(deletion.node.type())) {
					edits.remove(i--);
					continue;
				}

				// Remove excess deletions, whose parents are also deleted or moved
				// Note: we wait until the end in case they turn into Moves
				Highlight parentHighlight = colors.get(deletion.parent);
				if (parentHighlight == Highlight.Delete || parentHighlight == Highlight.Move ||
						parentHighlight == Highlight.Replaced) {
					edits.remove(i--);
					continue;
				}

				// Also remove any deletions that are implicit in an inertion's replacement
				for (Insertion inst : insertions) {
					if (inst.replacement == deletion.node) {
						edits.remove(i--);
						break;
					}
				}
				// NOTE: this has to be the last check, since it doesn't have a continue
			}
		}

//		printHighlight(node, colors);

		Collections.sort(edits);

		return edits;
	}

	private void addScriptReplacement(Insertion insertion, BiMap<Node, Node> mapping,
			List<EditHint> edits, Map<Node, Highlight> colors) {

		// To be eligible for a script replacement, an insertion must have no replacement and have a
		// script parent with a child at the insertion index
		if (insertion.replacement != null || !insertion.parent.hasType(config.script) ||
				insertion.parent.children.size() <= insertion.index) return;

		// Additionally the at the index of insert must be marked for deletion and be of a different
		// type than the inserted node
		Node deleted = insertion.parent.children.get(insertion.index);
		if (colors.get(deleted) != Highlight.Delete) return;
		if (deleted.type().equals(insertion.type)) return;

		// Lastly, there must be at least one child node that is paired to a child of the pair node
		// that is the cause for the insertion
		List<Node> nodeChildren = deleted.allChildren();
		List<Node> pairChildren = insertion.pair.allChildren();
		int matches = 0;
		for (Node n1 : nodeChildren) {
			for (Node n2 : pairChildren) {
				if (mapping.getFrom(n1) == n2) {
					matches++;
				}
			}
		}
		if (matches == 0) {
			return;
		}

		// In this case, we set the replacement, change the deleted node to replaced and remove the
		// deletion
		insertion.replacement = deleted;
		colors.put(deleted, Highlight.Replaced);

		// Find the deletion and remove it
		for (int i = 0; i < edits.size(); i++) {
			EditHint edit = edits.get(i);
			if (!(edit instanceof Deletion)) continue;
			if (deleted == ((Deletion) edit).node) {
				edits.remove(i);
				break;
			}
		}
	}

	// Code elements (blocks) are nodes which are not themselves scripts but have an ancestor
	// which is a script. This precludes snapshots, sprites, custom blocks, variables, etc,
	// while including blocks and lists
	private final boolean isCodeElement(Node node) {
		return !node.hasType(config.script) &&
				node.hasAncestor(new Node.TypePredicate(config.script));
	}

	public static DistanceMeasure getDistanceMeasure(HintConfig config) {
		return new ProgressDistanceMeasure(config.progressOrderFactor, 1, 0.25, config.script,
				config.literal);
	}

	private BiMap<Node, Node> findSolutionMapping(Node node) {
		DistanceMeasure dm = getDistanceMeasure(config);
		Node bestMatch = NodeAlignment.findBestMatch(node, solutions, dm);
		if (bestMatch == null) throw new RuntimeException("No matches!");

		NodeAlignment alignment = new NodeAlignment(node, bestMatch);
		alignment.calculateCost(dm);

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
	private static List<Node> preprocessSolutions(List<Node> allSolutions,
			final HintConfig config) {

		// First figure out how many scripts each solution has at each node
		final ListMap<Node, Integer> scriptCounts = new ListMap<>();
		for (Node node : allSolutions) {
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
		for (Node node : allSolutions) {
			Node copy = node.copy();
			copy.recurse(new Action() {
				@Override
				public void run(Node node) {
					if (!config.haveSideScripts.contains(node.type())) return;
					int median = scriptMedians.getCount(HintMap.toRootPath(node).root());
					List<Integer> sizes = new LinkedList<>();
					for (Node child : node.children) {
						if (child.hasType(config.script)) {
							sizes.add(child.treeSize());
						}
					}
					if (sizes.size() <= median) return;
					Collections.sort(sizes);
					int minSize = median == 0 ? Integer.MAX_VALUE :
						sizes.get(sizes.size() - median);
					for (int i = 0; i < node.children.size(); i++) {
						Node child = node.children.get(i);
						if (child.hasType(config.script) && child.treeSize() < minSize) {
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
			final List<Insertion> insertions, List<EditHint> edits, BiMap<Node, Node> mapping) {

		// Ensure that insertions with missing parents are paired last, giving priority to
		// actionable inserts when assigning candidates
		Collections.sort(insertions, new Comparator<Insertion>() {
			@Override
			public int compare(Insertion o1, Insertion o2) {
				if (o1.missingParent == o2.missingParent) return 0;
				return o1.missingParent ? 1 : -1;
			}
		});

		List<EditHint> toRemove = new LinkedList<>();
		List<EditHint> toAdd = new LinkedList<>();

		// For each deleted node, see if it should be inserted, and if so change it to a root move
		for (EditHint edit : edits) {
			if (!(edit instanceof Deletion)) continue;
			Deletion deletion = (Deletion) edit;
			Node deleted = deletion.node;

			// We're only interested in candidate nodes that are in scripts. This ignores things
			// like variables, sprites and custom block definitions
			if (!deleted.hasAncestor(new Node.TypePredicate(config.script))) continue;

			// If a deletion has been changed in the loop (e.g. to a move), ignore it
			if (colors.get(deleted) != Highlight.Delete) continue;

			for (Insertion insertion : insertions) {
				// We're also only interested in working with insertions into scripts
				if (!insertion.parent.hasAncestor(new Node.TypePredicate(config.script))) continue;

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
					// Also add this new pairing to the mapping
					mapping.put(deleted, insertion.pair);
					break;
				}

				// Moves are deletions that could be instead moved to perform a needed insertion
				// TODO: find the best match, not just the first
				if (sameType && insertion.candidate == null &&
						!insertion.type.equals(config.literal)) {
					colors.put(deleted, Highlight.Move);
					// Also mark children as moved so they can't be paired as well
					// TODO: this could be problematic if children are matched before their parents
					deleted.recurse(new Action() {
						@Override
						public void run(Node node) {
							colors.put(node, Highlight.Move);
						}
					});

					insertion.candidate = deleted;
					// Also add this new pairing to the mapping
					mapping.put(deleted, insertion.pair);
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
		public abstract void apply(List<Application> applications);

		public final Node parent;

		protected transient RuntimeException e;

		public EditHint(Node parent) {
			this.parent = parent;
		}

		@Override
		public String type() {
			return "highlight";
		}

		// If we get bad parameters, we want to log the trace of how it happened, but we don't
		// want to throw the exception until this hint is read, so as not to interrupt the
		// rest of the hint generation
		protected void storeException(String message) {
			try {
				throw new RuntimeException(message);
			} catch (RuntimeException e) {
				this.e = e;
			}
		}

		@Override
		public JSONObject data() {
			if (e != null) throw e;
			JSONObject data = new JSONObject();
			data.put("parent", Node.getNodeReference(parent));
			data.put("action", action());
			return data;
		}

		private LinkedList<String> getParentChildren() {
			if (parent == null) return new LinkedList<>();
			return new LinkedList<>(Arrays.asList(parent.getChildArray()));
		}

		@Override
		public String from() {
			if (e != null) throw e;
			LinkedList<String> items = getParentChildren();
			return action() + ": " + rootString(parent) + ": " + items;
		}

		@Override
		public String to() {
			if (e != null) throw e;
			LinkedList<String> items = getParentChildren();
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

		public static void applyEdits(Node node, List<EditHint> hints) {
			List<Application> applications = new ArrayList<>();
			for (EditHint hint : hints) hint.apply(applications);

			// Start with hints in reverse order, since multiple inserts at the same index should
			// be applies in reverse to create the correct final order
			Collections.reverse(applications);
			Collections.sort(applications, new Comparator<Application>() {
				@Override
				public int compare(Application o1, Application o2) {
					// Apply edits to longer root paths first
					int rpc = Integer.compare(
							o1.parent.rootPathLength(), o2.parent.rootPathLength());
					if (rpc != 0) return -rpc;

					return -Integer.compare(o1.index, o2.index);
				}
			});
			for (Application application : applications) application.action.apply();
		}
	}

	private static class Application {
		final Node parent;
		final int index;
		final EditAction action;

		public Application(Node parent, int index, EditAction action) {
			this.parent = parent;
			this.index = index;
			this.action = action;
		}
	}

	private interface EditAction {
		void apply();
	}

	public static class Insertion extends EditHint {
		public final String type;
		public final int index;
		public final boolean missingParent;

		// Used to mark the pair node in the solution this insertion represents
		private final transient Node pair;

		/** The node the inserted node should replace (in the same location) */
		public Node replacement;
		/** A candidate node elsewhere that could be used to do the replacement */
		public Node candidate;

		@Override
		public String action() {
			return "insert";
		}

		public Insertion(Node parent, Node pair, int index) {
			this(parent, pair, index, false);
		}

		public Insertion(Node parent, Node pair, int index, boolean missingParent) {
			super(parent);
			this.type = pair.type();
			this.index = index;
			this.missingParent = missingParent;
			this.pair = pair;
			if (index > parent.children.size()) {
				storeException("Insert index out of range");
			}
		}

		@Override
		public JSONObject data() {
			JSONObject data = super.data();
			data.put("missingParent", missingParent);
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

		@Override
		protected String rootString(Node node) {
			// Mark missing parents in the preview
			String rootString = super.rootString(node);
			if (missingParent) {
				rootString = "{" + rootString + "}";
			}
			return rootString;
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
				text += " using " + super.rootString(candidate);
			}
			return text;
		}

		@Override
		public String to() {
			String text = super.to();
			if (candidate != null) {
				text += " using " + super.rootString(candidate);
			}
			return text;
		}

		@Override
		protected double priority() {
			return 5 + (candidate == null ? 0  : 1) + (replacement == null ? 0 : 1);
		}

		@Override
		public void apply(List<Application> applications) {
			final Node toInsert;
			if (candidate != null) {
				toInsert = candidate.copyWithNewParent(parent);

				applications.add(new Application(candidate.parent, candidate.index(),
						new EditAction() {
					@Override
					public void apply() {
						// It's possible this has already been removed (e.g. as a replacement for
						// another insert), so we only remove it if it's still a child of its parent
						int index = candidate.index();
						if (index >= 0) candidate.parent.children.remove(index);
					}
				}));
			} else {
				toInsert = new Node(parent, type);
			}

			// If the parent is missing, we stop after removing the candidate
			if (missingParent) return;

			applications.add(new Application(parent, index, new EditAction() {
				@Override
				public void apply() {
					if (replacement != null) {
						int index = replacement.index();
						if (index >= 0) parent.children.remove(index);
					}
					parent.children.add(index, toInsert);
				}
			}));
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

		@Override
		public void apply(List<Application> applications) {
			final int index = node.index();
			applications.add(new Application(parent, index, new EditAction() {
				@Override
				public void apply() {
					node.parent.children.remove(index);
				}
			}));
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
			if (index < 0 || index > node.parent.children.size()) {
				storeException("Reorder index out of bounds: " + index + " for size " +
						node.parent.children.size());
			}

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

		@Override
		public void apply(List<Application> applications) {
			final int rIndex = node.index();
			applications.add(new Application(parent, rIndex, new EditAction() {
				@Override
				public void apply() {
					node.parent.children.remove(rIndex);
				}
			}));
			final int aIndex = index;
			applications.add(new Application(parent, aIndex, new EditAction() {
				@Override
				public void apply() {
					node.parent.children.add(aIndex, node);
				}
			}));
		}
	}

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}
}
