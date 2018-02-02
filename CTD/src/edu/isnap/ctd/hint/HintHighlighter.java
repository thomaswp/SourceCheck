package edu.isnap.ctd.hint;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.Ordering.Addition;
import edu.isnap.ctd.hint.Ordering.OrderMatrix;
import edu.isnap.ctd.hint.debug.HintDebugInfo;
import edu.isnap.ctd.hint.edit.Deletion;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.hint.edit.Insertion;
import edu.isnap.ctd.hint.edit.Priority;
import edu.isnap.ctd.hint.edit.Reorder;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.Cast;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.DistanceMeasure;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.ctd.util.NodeAlignment.ProgressDistanceMeasure;
import edu.isnap.ctd.util.NullStream;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;

public class HintHighlighter {

	public static enum Highlight {
		Good, Add, Delete, Order, Move, Replaced
	}

	public PrintStream trace = NullStream.instance;

	private final List<Node> solutions;
	private final HintConfig config;
	private final HintMap hintMap;

	private final Map<Node, Map<String, Double>> nodePlacementTimes;

	public HintHighlighter(HintMap hintMap) {
		this(hintMap.solutions, hintMap.config, hintMap);
	}

	public HintHighlighter(List<Node> solutions, HintConfig config) {
		this(solutions, config, null);
	}

	public HintHighlighter(List<Node> solutions, HintConfig config, HintMap hintMap) {
		// Make a copy of the nodePlacementTimes so we can modify them when we replace nodes in the
		// preprocessSolutions method
		this.nodePlacementTimes = hintMap == null ? null :
			new IdentityHashMap<>(hintMap.nodePlacementTimes);
		if (solutions.isEmpty()) throw new IllegalArgumentException("Solutions cannot be empty");
		this.solutions = config.preprocessSolutions ?
				preprocessSolutions(solutions, config, nodePlacementTimes) : solutions;
		this.config = config;
		this.hintMap = hintMap;
	}

	public HintDebugInfo debugHighlight(Node node) {
		Mapping mapping = findSolutionMapping(node);
		List<EditHint> edits = highlight(node, mapping);
		return new HintDebugInfo(mapping, edits);
	}

	public List<EditHint> highlight(Node node) {
		Mapping mapping = findSolutionMapping(node);
		return highlight(node, mapping);
	}

	public List<EditHint> highlightWithPriorities(Node node) {
		Mapping mapping = findSolutionMapping(node);
		List<EditHint> hints = highlight(node, mapping);
		assignPriorities(mapping, hints);
		return hints;
	}

	public List<EditHint> highlight(Node node, final Mapping mapping) {
		return highlight(node, mapping, true);
	}

	public List<EditHint> highlight(Node node, final Mapping mapping,
			boolean reuseDeletedBlocks) {
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
				if (pair == null) return;

				Node parent = node.parent;
				boolean parentMatches = parent == null ||
						mapping.getFrom(parent) == pair.parent;
				Highlight highlight = parentMatches ? Highlight.Good : Highlight.Move;
				colors.put(node, highlight);

				if (!(highlight == Highlight.Move && config.canMove(node))) return;

				Node moveParent = mapping.getTo(pair.parent);
				if (moveParent == null) {
					Insertion insertion = new Insertion(pair.parent, pair, pair.index(),
							mapping.getMappedValue(node, true), true);
					insertion.candidate = node;
					edits.add(insertion);
				} else {
					// For those with a matching parent, we need to insert them where
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

					Insertion insertion = new Insertion(moveParent, pair, insertIndex,
							mapping.getMappedValue(node, true));
					insertion.candidate = node;
					// If this is a code element parent, inserting the node should replace
					// the current node at this index
					if (config.hasFixedChildren(moveParent) &&
							insertIndex < moveParent.children.size()) {
						insertion.replaced = moveParent.children.get(insertIndex);
					}
					edits.add(insertion);
				}
			}
		});

		// Find nodes that need to be reordered (Order)
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				// Start by finding parent nodes that have a pair
				Node pair = mapping.getFrom(node);
				if (pair == null) return;

				// For clarity, this routine handles reorders for nodes that _are_ children
				// of a paired code element node (first), then it handles reorders for the
				// _children of_ non-code-element nodes (second)

				// Argument nodes are easy, since their order should be the same as their pair
				if (node.parent != null && node.parent == mapping.getTo(pair.parent) &&
						config.hasFixedChildren(node.parent)) {

					int pairIndex = pair.index();
					// If the indices don't match and the pair's index fits within the parent's
					// arguments, reorder the node
					if (node.index() != pairIndex && pairIndex < node.parent.children.size()) {
						// I think this should happen even if we suppress the reorder, but I'm
						// not 100% sure...
						colors.put(node, Highlight.Order);

						Reorder reorder = new Reorder(node, pairIndex, true);
						if (!reorder.shouldSuppress(mapping)) edits.add(reorder);

					}
				}

				// The below is for non-code-elements (scripts), since it involves insertion
				if (config.hasFixedChildren(node)) return;

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
						Reorder reorder = new Reorder(toReorder, index, false);
						if (!reorder.shouldSuppress(mapping)) edits.add(reorder);
					}
				}
			}
		});

		// Insertions: This time, iterate over the children of the target solution
		Node nodeMatch = mapping.getFrom(node);
		nodeMatch.recurse(new Action() {
			@Override
			public void run(Node pair) {
				// See if the pair has a corresponding node in the student's tree
				Node node = mapping.getTo(pair);
				if (node != null) {
					// If so, we iterate through its children and add them if they're not there
					int insertIndex = 0;
					for (Node child : pair.children) {
						Node originalChild = mapping.getTo(child);
						if (originalChild != null) {
							// For paired children, we just update the insertion index and continue
							if (originalChild.parent == node) {
								insertIndex = originalChild.index() + 1;
							}
							continue;
						}

						// Otherwise we add an insertion

						if (config.hasFixedChildren(node)) {
							// For elements with fixed children, we use the pair-child's index
							insertIndex = child.index();

							// For these nodes, we cannot insert past the parent's size
							// (e.g. add an element to a list)
							// TODO: This should be supported eventually
							if (insertIndex >= node.children.size()) {
								break;
							}
							insertIndex = Math.max(0, insertIndex);
						}

						Insertion insertion = new Insertion(node, child, insertIndex,
								mapping.getMappedValue(child, false));
						edits.add(insertion);
						// If the node is being inserted in a code element then it replaces
						// whatever already exists at this index in the parent
						// It's possible that a list/custom block will have 0 args, in which
						// case there will be no replacement.
						if (config.hasFixedChildren(node) && node.children.size() > 0) {
							insertion.replaced = node.children.get(insertIndex);
						}
						// If this is a non-script, we treat this new insertion as a "match"
						// and increment the insert index if possible
						if (!config.isOrderInvariant(node.type())) {
							// We do have to make sure not to go out of bounds, though
							insertIndex = Math.min(node.children.size(), insertIndex + 1);
						}
					}
				} else if (mapping.getTo(pair.parent) == null) {
					// If not, and the pair's parent has no corresponding node either, it won't
					// be added by the above code, so we need to add it. Unfortunately, there's
					// nowhere to add it to the student's code, since it's parent has no
					// corresponding node, so we use the pair-parent and mark it as a special case.
					// It's useful to list these insertions anyway, since it allows us to mark nodes
					// as Move instead of Delete when they're used in not-yet-added parents

					// This really only makes sense for code blocks
					if (!config.canMove(pair)) return;


					Insertion insertion = new Insertion(pair.parent, pair, pair.index(),
							mapping.getMappedValue(pair, false), true);
					edits.add(insertion);
				}
			}
		});


		if (reuseDeletedBlocks) {
			handleInsertionsAndMoves(colors, extractInsertions(edits), edits, mapping);
		}

		// If any insert into a script should contain the children of the existing node at that
		// index, we want to express that as a replacement
		for (Insertion insertion : extractInsertions(edits)) {
			addScriptReplacement(insertion, mapping, edits, colors);
		}

		extractSubedits(edits);

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
				if (config.isHarmlessType(deletion.node.type())) {
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
				for (Insertion inst : extractInsertions(edits)) {
					if (inst.replaced == deletion.node) {
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

	private void extractSubedits(final List<EditHint> edits) {
		if (!config.createSubedits) return;

		// Find any insertion that has a missing parent which will be inserted by another insertion.
		// Mark that as a subedit of the parent, so it will only be executed afterwards
		List<Insertion> insertions = extractInsertions(edits);
		for (Insertion parent : insertions) {
			for (int i = 0; i < edits.size(); i++) {
				Insertion child = Cast.cast(edits.get(i), Insertion.class);
				if (child == null || child.parentPair != parent.pair) continue;

				// We only use children as subedits if they have a candidate, since otherwise we
				// would just every hint as a nested hint
				boolean insertForCandidate = !parent.missingParent && child.candidate != null;
				// Also add nodes that should be auto-inserted, but those without candidates
				// TODO: Somehow insertions without a candidate get through here and show up later
				// with a candidate..?
				boolean insertAuto = config.shouldAutoAdd(child.pair) && child.candidate == null;
				if (insertForCandidate || insertAuto) {
					edits.remove(i--);
					parent.subedits.add(child);
				}
			}
		}
	}

	private List<Insertion> extractInsertions(final List<EditHint> edits) {
		List<Insertion> insertions;
		insertions = edits.stream()
				.filter(e -> e instanceof Insertion)
				.map(e -> (Insertion) e)
				.collect(Collectors.toList());
		return insertions;
	}

	@SuppressWarnings("deprecation")
	// TODO: Make not snap-specific
	private void addScriptReplacement(Insertion insertion, BiMap<Node, Node> mapping,
			List<EditHint> edits, Map<Node, Highlight> colors) {

		// To be eligible for a script replacement, an insertion must have no replacement and have a
		// script parent with a child at the insertion index
		if (insertion.replaced != null || !config.isScript(insertion.parent.type()) ||
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
		// Because we're replacing in a script, we may want to keep the original node's children
		// when we replace it, but it depends on where we find our matches
		// TODO: This isn't a very comprehensive solution: we really need to deal with children of
		// moved blocks in a more thorough manner
		boolean keepChildren = false;
		int matches = 0;
		for (Node n1 : nodeChildren) {
			// Don't match literals
			// TODO: This produces empty hints when working with lists or literals
			if (config.isValueless(n1.type())) continue;
			for (Node n2 : pairChildren) {
				if (mapping.getFrom(n1) == n2) {
					matches++;
					if (n2.parent == insertion.pair ||
							(n2.parent != null && config.isScript(n2.parent.type()))) {
						// If the match comes from a direct child of the pair (or a script that is)
						// we want to keep the children.
						keepChildren = true;
					}
				}
			}
		}
		if (matches == 0) {
			return;
		}

		// In this case, we set the replacement, change the deleted node to replaced and remove the
		// deletion
		insertion.replaced = deleted;
		insertion.keepChildrenInReplacement = keepChildren;
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

	public DistanceMeasure getDistanceMeasure() {
		return getDistanceMeasure(config);
	}

	public static DistanceMeasure getDistanceMeasure(HintConfig config) {
		return new ProgressDistanceMeasure(config);
	}

	public Mapping findSolutionMapping(Node node) {
		long startTime = System.currentTimeMillis();

		List<Mapping> bestMatchList = findBestMappings(node, 1);
		if (bestMatchList.size() == 0) throw new RuntimeException("No matches!");
		Mapping bestMatch = bestMatchList.get(0);

		if (trace != null) {
			trace.println("------------------------------");
//			trace.println(node.prettyPrint());
//			trace.println("++++++++++");
			trace.println("Time to match: " + (System.currentTimeMillis() - startTime));
			trace.println(bestMatch.prettyPrint(true));
			trace.println(bestMatch.itemizedCostString());
			bestMatch.printValueMappings(trace);
		}
		return bestMatch;
	}

	private List<Mapping> findBestMappings(Node node, int maxReturned) {
		DistanceMeasure dm = getDistanceMeasure(config);
		List<Node> filteredSolutions = solutions;
		if (hintMap != null && hintMap.ruleSet != null) {
			RuleSet.trace = trace;
			filteredSolutions = hintMap.ruleSet.filterSolutions(solutions, node);
			if (filteredSolutions.size() == 0) throw new RuntimeException("No solutions!");
		}
		return NodeAlignment.findBestMatches(node, filteredSolutions, dm, config, maxReturned);
	}


	/**
	 * Remove side scripts from the submitted solutions. We do this to prevent side-script matches
	 * from have too much influence in the matching process.
	 */
	@SuppressWarnings("deprecation")
	// TODO: Rework to be not snap-specific
	private static List<Node> preprocessSolutions(List<Node> allSolutions, HintConfig config,
			Map<Node, Map<String, Double>> nodePlacementTimes) {

		// TODO: This doesn't work well with multi-script and multi-sprite solutions

		// First figure out how many scripts each solution has at each node
		final ListMap<Node, Integer> scriptCounts = new ListMap<>();
		for (Node node : allSolutions) {
			node.recurse(new Action() {
				@Override
				public void run(Node node) {
					if (!config.hasSideScripts(node.type())) return;
					int scripts = 0;
					for (Node child : node.children) {
						if (config.isScript(child.type())) {
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
					if (!config.hasSideScripts(node.type())) return;
					int median = scriptMedians.getCount(HintMap.toRootPath(node).root());
					List<Integer> sizes = new LinkedList<>();
					for (Node child : node.children) {
						if (config.isScript(child.type())) {
							sizes.add(child.treeSize());
						}
					}
					if (sizes.size() <= median) return;
					Collections.sort(sizes);
					int minSize = median == 0 ? Integer.MAX_VALUE :
						sizes.get(sizes.size() - median);
					for (int i = 0; i < node.children.size(); i++) {
						Node child = node.children.get(i);
						if (config.isScript(child.type()) && child.treeSize() < minSize) {
//							trace.println("Preprocess removed: " + node.children.get(i));
							node.children.remove(i--);
						}
					}
				}
			});
			if (nodePlacementTimes != null) {
				// Update references to this node in the nodeCreationPercs map
				// Really, we shouldn't be using the Node as the key, but rather some ID...
				nodePlacementTimes.put(copy, nodePlacementTimes.remove(node));
			}
			solutions.add(copy);
		}

		return solutions;
	}

	private void handleInsertionsAndMoves(final IdentityHashMap<Node, Highlight> colors,
			final List<Insertion> insertions, List<EditHint> edits, Mapping mapping) {

		DistanceMeasure dm = getDistanceMeasure(config);

		List<Deletion> deletions = new LinkedList<>();
		for (EditHint edit : edits) {
			if (edit instanceof Deletion) deletions.add((Deletion) edit);
		}
		// Pair deletions with parents first to ensure we match parents before children
		Collections.sort(deletions, new Comparator<Deletion>() {
			@Override
			public int compare(Deletion o1, Deletion o2) {
				return Integer.compare(o1.node.depth(), o2.node.depth());
			}
		});

		List<EditHint> toRemove = new LinkedList<>();
		List<EditHint> toAdd = new LinkedList<>();

		// For each deleted node, see if it should be inserted, and if so change it to a root move
		for (Deletion deletion : deletions) {
			Node deleted = deletion.node;

			// We're only interested in candidate nodes that can be moved around in the solution
			if (!config.canMove(deleted)) continue;

			// If a deletion has been changed in the loop (e.g. to a move), ignore it
			if (colors.get(deleted) != Highlight.Delete) continue;

			Insertion bestMoveMatch = null;
			double bestMoveCost = Double.MAX_VALUE;

			for (Insertion insertion : insertions) {
				// Only match nodes with the same type and mapped value (if applicable/non-null)
				if (!(deleted.hasType(insertion.type) &&
						StringUtils.equals(
								mapping.getMappedValue(deleted, true),
								insertion.value))) {
					continue;
				}

				boolean matchParent = deleted.parent == insertion.parent;

				// If it's an insertion/deletion of the same type in the same parent, it's
				// just a reorder
				// TODO: why do we restrict this to flexible-order nodes (e.g. scripts)?
				if (matchParent && !config.hasFixedChildren(insertion.parent) &&
						insertion.index != deleted.index()) {
					colors.put(deleted, Highlight.Move);
					Reorder reorder = new Reorder(deleted, insertion.index,
							config.hasFixedChildren(deleted.parent));
					if (!reorder.shouldSuppress(mapping)) toAdd.add(reorder);
					toRemove.add(deletion);
					toRemove.add(insertion);
					// Set the insertion candidate, so it's not somehow used later in the loop
					insertion.candidate = deleted;
					// Also add this new pairing to the mapping
					mapping.put(deleted, insertion.pair);

					// If we can do a reorder, ensure no move matching occurs
					bestMoveMatch = null;
					break;
				}

				// Moves are deletions that could be instead moved to perform a needed insertion
				if (insertion.candidate == null) {
					double cost = NodeAlignment.getSubCostEsitmate(deleted, insertion.pair, dm);
					// Ensure that insertions with missing parents are paired last, giving priority
					// to actionable inserts when assigning candidates
					if (insertion.missingParent) cost += Double.MAX_VALUE / 2;
					if (cost < bestMoveCost) {
						bestMoveCost = cost;
						bestMoveMatch = insertion;
					}
				}
			}

			if (bestMoveMatch != null) {
				colors.put(deleted, Highlight.Move);
				// Also mark children as moved so they can't be paired as well
				deleted.recurse(new Action() {
					@Override
					public void run(Node node) {
						colors.put(node, Highlight.Move);
					}
				});

				bestMoveMatch.candidate = deleted;
				// Also add this new pairing to the mapping
				mapping.put(deleted, bestMoveMatch.pair);
				toRemove.add(deletion);
			}
		}

		edits.removeAll(toRemove);
		edits.addAll(toAdd);


	}

	public void assignPriorities(Mapping bestMatch, List<EditHint> hints) {
		Node node = bestMatch.from;
		// TODO: why completely recalculate this?
		// TODO: Get maxReturned from config!
		List<Mapping> bestMatches = findBestMappings(node, 10);

		if (!bestMatches.stream().anyMatch(m -> m.to == bestMatch.to)) {
			throw new IllegalArgumentException("bestMatch must be a member of hints");
		}

		// Get counts of how many times each edit appears in the top matches
		CountMap<EditHint> hintCounts = new CountMap<>();
		bestMatches.stream()
		// Use a set so duplicates from a single target solution don't get double-counted
		.map(m -> new HashSet<>(highlight(node, m)))
		.forEach(set -> hintCounts.incrementAll(set));

		for (EditHint hint : hints) {
			Priority priority = new Priority();
			priority.consensusNumerator = hintCounts.getCount(hint);
			priority.consensusDenominator = bestMatches.size();
			hint.priority = priority;
		}

		Map<Mapping, Mapping> bestToGoodMappings = new HashMap<>();
		if (nodePlacementTimes != null && nodePlacementTimes.containsKey(bestMatch.to)) {
			for (Mapping match : bestMatches) {
				if (bestMatch.to == match.to) continue;
				Map<String, Double> creationPercs = nodePlacementTimes.get(match.to);
				if (creationPercs == null) continue;
				Mapping mapping = new NodeAlignment(bestMatch.to, match.to, config)
						.calculateMapping(getDistanceMeasure());
				bestToGoodMappings.put(match, mapping);
			}
		}

		for (EditHint hint : hints) {
			if (nodePlacementTimes != null && nodePlacementTimes.containsKey(bestMatch.to)) {
				Map<String, Double> creationPercs = nodePlacementTimes.get(bestMatch.to);
				Node priorityToNode = hint.getPriorityToNode(bestMatch);
				if (creationPercs != null && priorityToNode != null) {
					List<Double> percs = new ArrayList<>();
					percs.add(creationPercs.get(priorityToNode.id));

					for (Mapping match : bestToGoodMappings.keySet()) {
						Node from = bestToGoodMappings.get(match).getFrom(priorityToNode);
						if (from == null) continue;
						creationPercs = nodePlacementTimes.get(match.to);
						percs.add(creationPercs.get(from.id));
					}

					hint.priority.creationTime = percs.stream()
							.filter(d -> d != null).mapToDouble(d -> d)
							.average();
				}
			}
		}

		findOrderingPriority(hints, node, bestMatch);
	}

	private void findOrderingPriority(List<EditHint> hints, Node node, Mapping bestMatch) {
		if (hintMap == null || hintMap.orderMatrix == null) return;

		CountMap<String> nodeLabelCounts = Ordering.countLabels(node);
		CountMap<String> matchLabelCounts = Ordering.countLabels(bestMatch.to);
		Map<Insertion, Addition> insertionAdditions = new HashMap<>();
		for (EditHint hint : hints) {
			if (hint instanceof Insertion) {
				Insertion insertion = (Insertion) hint;
				if (insertion.pair == null) {
					System.err.println("Insertion w/o pair: " + insertion);
					continue;
				}
				String label = Ordering.getLabel(insertion.pair);
				// If the label is null, this is not a meaningful node to track the ordering
				if (label == null) continue;

				if (insertion.replaced != null && insertion.replaced.hasType(insertion.type)) {
					// If this insertion just replaces a node with the same type, it does not
					// it will not change the index count
					continue;
				}

				// Get the number of times this item appears in the student's code
				int count = nodeLabelCounts.getCount(label);
				// If we are inserting without a candidate, this node will increase that count by 1
				if (insertion.candidate == null ||
						!label.equals(Ordering.getLabel(insertion.candidate))) {
					count++;
				}
				// But do not increase the count beyond the number present in the target solution
				// This ensures at least one ordering should always be found in the below code
				count = Math.min(count, matchLabelCounts.get(label));

				Addition addition = new Addition(label, count);
				insertionAdditions.put(insertion, addition);
			}
		}

		List<Insertion> insertions = new ArrayList<>(insertionAdditions.keySet());

		OrderMatrix matrix = hintMap.orderMatrix;
		for (Insertion insertion : insertions) {
			if (!insertionAdditions.containsKey(insertion)) continue;

			List<Addition> additions = matrix.additions();
			Addition insertAddition = insertionAdditions.get(insertion);
			int insertIndex = additions.indexOf(insertAddition);
			if (insertIndex < 0) continue;

			int satisfiedPrereqs = 0;
			int totalPrereqs = 0;
			int unsatisfiedPostreqs = 0;
			int totalPostreqs = 0;

			// TODO config;
			double threshhold = 0.85;

			for (int i = 0; i < additions.size(); i++) {
				double percOrdered = matrix.getPercOrdered(i, insertIndex);
				Addition addition = additions.get(i);
				if (percOrdered >= threshhold) {
					if (matchLabelCounts.getCount(addition.label) >= addition.count) {
						totalPrereqs++;
						if (nodeLabelCounts.getCount(addition.label) >= addition.count) {
							satisfiedPrereqs++;
						}
					}
				} else if (percOrdered < 1 - threshhold) {
					totalPostreqs++;
					if (nodeLabelCounts.getCount(addition.label) >= addition.count) {
						unsatisfiedPostreqs++;
					}
				}
			}

			insertion.priority.prereqsNumerator = satisfiedPrereqs;
			insertion.priority.prereqsDenominator = totalPrereqs;
			insertion.priority.postreqsNumerator = unsatisfiedPostreqs;
			insertion.priority.postreqsDenominator = totalPostreqs;
		}

		calculateOrderingRanks(insertions, insertionAdditions);
	}

	private void calculateOrderingRanks(List<Insertion> insertions,
			Map<Insertion, Addition> insertionMap) {
		if (hintMap == null || hintMap.orderMatrix == null) return;

		OrderMatrix matrix = hintMap.orderMatrix;
		List<Addition> additions = matrix.additions();
		List<Insertion> orderedInsertions = insertionMap.keySet().stream()
				.filter(insertion -> additions.contains(insertionMap.get(insertion)))
				.collect(Collectors.toList());
		Collections.sort(orderedInsertions, (a, b) -> {
			Addition additionA = insertionMap.get(a);
			Addition additionB = insertionMap.get(b);
			// TODO: I think this may be able to produce inconsistent orderings
			return matrix.getPercOrdered(
					additions.indexOf(additionA), additions.indexOf(additionB)) >= 0.50 ?
							-1 : 1;
		});
		for (int i = 0; i < orderedInsertions.size(); i++) {
			Priority priority = orderedInsertions.get(i).priority;
			priority.orderingNumerator = i + 1;
			priority.orderingDenominator = orderedInsertions.size();

		}
	}

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}

	public List<EditHint> highlightStringEdit(Node node) {
		String[] nodeSeq = node.depthFirstIteration();

		double minDis = Double.MAX_VALUE;
		Node best = null;
		for (Node solution : solutions) {
			double dis = Alignment.alignCost(nodeSeq, solution.depthFirstIteration());
			if (dis < minDis) {
				best = solution;
				minDis = dis;
			}
		}

		String[] bestSeq = best.depthFirstIteration();
		List<int[]> alignPairs = Alignment.alignPairs(nodeSeq, bestSeq, 1, 1, 1);

		Mapping mapping = new Mapping(node, best, config);
		for (int[] a : alignPairs) {
			if (a[0] == -1 || a[1] == -1) continue;
			Node from = node.nthDepthFirstNode(a[0]);
			Node to = best.nthDepthFirstNode(a[1]);
			mapping.put(from, to);
		}

		return highlight(node, mapping);
	}
}
