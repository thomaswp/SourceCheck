package edu.isnap.sourcecheck;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import edu.isnap.ctd.hint.HintMap;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.HintDebugInfo;
import edu.isnap.hint.IDataConsumer;
import edu.isnap.hint.IDataModel;
import edu.isnap.hint.SolutionsModel;
import edu.isnap.hint.util.Alignment;
import edu.isnap.hint.util.Cast;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.NodeAlignment.ProgressDistanceMeasure;
import edu.isnap.sourcecheck.edit.Deletion;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.sourcecheck.edit.Reorder;
import edu.isnap.sourcecheck.priority.HintPrioritizer;
import edu.isnap.sourcecheck.priority.RuleSet;
import edu.isnap.sourcecheck.priority.RulesModel;
import edu.isnap.util.map.BiMap;
import edu.isnap.util.map.CountMap;
import edu.isnap.util.map.ListMap;

public class HintHighlighter {

	public static enum Highlight {
		Good, Add, Delete, Order, Move, Replaced
	}

	public PrintStream trace = NullStream.instance;

	public final HintConfig config;
	public final HintData hintData;
	private final List<Node> solutions;

	public final static IDataConsumer DataConsumer = new IDataConsumer() {
		@Override
		public IDataModel[] getRequiredData(HintData data) {
			return new IDataModel[] {
					new SolutionsModel(),
					new RulesModel(),
			};
		}
	};

	private static HintData fromSolutions(Collection<Node> solutions, HintConfig config) {
		HintData data = new HintData(null, config, 1, DataConsumer);
		for (Node solution : solutions) data.addTrace(null, Collections.singletonList(solution));
		data.finished();
		return data;
	}

	public HintHighlighter(Collection<Node> solutions, HintConfig config) {
		this(fromSolutions(solutions, config));
	}

	public HintHighlighter(HintData hintData) {
		// Make a copy of the nodePlacementTimes so we can modify them when we replace nodes in the
		// preprocessSolutions method
//		this.nodePlacementTimes = hintMap == null ? null :
//			new IdentityHashMap<>(hintMap.nodePlacementTimes);
		Collection<Node> solutions = hintData.getModel(SolutionsModel.class).getSolutions();
		if (solutions.isEmpty()) throw new IllegalArgumentException("Solutions cannot be empty");
		List<Node> solutionsCopy = new ArrayList<>();
		synchronized (solutions) {
			for (Node solution : solutions) {
				solutionsCopy.add(solution.copy());
			}
		}
		solutions = solutionsCopy;

		this.config = hintData.config;
		this.hintData = hintData;
		this.solutions = config.preprocessSolutions ?
				preprocessSolutions(solutions, config, null) :
					new ArrayList<>(solutions);
	}

	public HintDebugInfo debugHighlight(Node node) {
		Mapping mapping = findSolutionMapping(node);
		List<EditHint> edits = highlightWithPriorities(node, mapping);
		return new HintDebugInfo(mapping, edits);
	}

	public List<EditHint> highlight(Node node) {
		Mapping mapping = findSolutionMapping(node);
		return highlight(node, mapping);
	}

	/**
	 * @param node current student's code for which we show hints
	 * @return a list of edits with priorities
	 */
	public List<EditHint> highlightWithPriorities(Node node) {
		Mapping mapping = findSolutionMapping(node);
		return highlightWithPriorities(node, mapping);
	}

	public List<EditHint> highlightWithPriorities(Node node, Mapping mapping) {
		List<EditHint> hints = highlight(node, mapping);
		new HintPrioritizer(this).assignPriorities(mapping, hints);
		return hints;
	}

	public List<EditHint> highlight(Node node, final Mapping mapping) {
		return highlight(node, mapping, true);
	}

	/**
	 * @param node current student's code for which we show hints
	 * @param mapping
	 * @param reuseDeletedBlocks
	 * @return
	 */
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
							// When moving/reordering, we don't change the value of the node
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
							// When moving/reordering, we don't change the value of the node
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
							// TODO: This keep happening!
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
								getInsertValue(child, mapping));
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

					Insertion insertion = new Insertion(pair.parent, pair, pair.index(),
							getInsertValue(pair, mapping), true);
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
			inferReplacements(insertion, mapping, edits, colors);
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

	private String getInsertValue(Node pair, Mapping mapping) {
		String value = mapping.getMappedValue(pair, false);
		if (value == null && config.suggestNewMappedValues) {
			value = pair.value;
		}
		return value;
	}

	private void extractSubedits(final List<EditHint> edits) {
		if (!config.createSubedits) return;

		// Find any insertion that has a missing parent which will be inserted by another insertion.
		// Mark that as a subedit of the parent, so it will only be executed afterwards
		List<Insertion> insertions = extractInsertions(edits);
		for (Insertion parent : insertions) {
			if (parent.keepChildrenInReplacement) continue;
			for (int i = 0; i < edits.size(); i++) {
				Insertion child = Cast.cast(edits.get(i), Insertion.class);
				if (child == null || child.parentPair != parent.pair) continue;

				// We only use children as subedits if they have a candidate, since otherwise we
				// would just give every hint as a nested hint
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

	private void inferReplacements(Insertion insertion, BiMap<Node, Node> mapping,
			List<EditHint> edits, Map<Node, Highlight> colors) {
		// To be eligible for a script replacement, an insertion must have no replacement and have a
		// script parent with a child at the insertion index
		if (insertion.replaced != null || config.hasFixedChildren(insertion.parent) ||
				insertion.parent.children.size() <= insertion.index) return;

		// Additionally the at the index of insert must be marked for deletion and be of a different
		// type than the inserted node
		Node deleted = insertion.parent.children.get(insertion.index);
		EditHint deletion = edits.stream()
				.filter(edit -> edit instanceof Deletion && ((Deletion) edit).node == deleted)
				.findFirst().orElse(null);
		if (colors.get(deleted) != Highlight.Delete) return;
		if (deleted.type().equals(insertion.type) &&
				StringUtils.equals(deleted.value, insertion.value)) {
			return;
		}

//		System.out.println(deleted + " => " + insertion);
//		edits.stream().filter(edit -> edit.parent == insertion.parent).forEach(System.out::println);
//		System.out.println("-----");

		boolean match = deleted.type().equals(insertion.type);

		// We have a fairly loose standard for children matching. We see if they share any immediate
		// children with the same type, and if so we match them. (Note that this is a
		boolean keepChildren = false;
		for (Node n1 : deleted.children) {
			String type1 = n1.type();
			if (config.isValueless(type1)) continue;
			for (Node n2 : insertion.pair.children) {
				if (!n2.hasType(type1)) continue;
				match = true;
				keepChildren = true;
				break;
			}
		}

		// If all children of the deleted node's parent should be deleted and that parent only
		// has one child in the selected solution, we treat this as a replacement, under the notion
		// that this will be more semantically meaningful than a separate insertion and deletion
		List<Node> deletedSiblings = deleted.parent.children;
		if (!match && insertion.pair.parent.children.size() == 1 &&
				mapping.getFrom(deleted.parent) == insertion.pair.parent &&
				deletedSiblings.stream().allMatch(sib -> colors.get(sib) == Highlight.Delete)) {
			match = true;
		}

		// If there are no other edits for this parent besides the insertion and deletion to be
		// combined, this is likely a replacement
		if (config.createSingleLineReplacements) {
			if (!edits.stream().anyMatch(edit ->
					edit.parent == insertion.parent &&
					!edit.equals(insertion) &&
					!edit.equals(deletion))) {
				match = true;
			}
		}

		if (!match) return;

		// In this case, we set the replacement, change the deleted node to replaced and remove the
		// deletion
		insertion.replaced = deleted;
		// For now, we always keep the children, though this may not always be the best choice
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

	/**
	 * @param node current student's code for which we show hints
	 * @return the mapping from "node" to the closest solution
	 */
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

	/**
	 * @param node current student's code for which we show hints
	 * @param maxReturned the number of the closest codes to return
	 * @return a mapping from "node" to maxReturned number of closest "solutions"
	 */
	public List<Mapping> findBestMappings(Node node, int maxReturned) {
		DistanceMeasure dm = getDistanceMeasure(config);
		List<Node> filteredSolutions = getSolutions();

		RulesModel rulesModel = hintData.getModel(RulesModel.class);
		RuleSet ruleSet = rulesModel == null ? null : rulesModel.getRuleSet();

		if (ruleSet != null) {
			RuleSet.trace = trace;
			filteredSolutions = ruleSet.filterSolutions(getSolutions(), node);
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
	private static List<Node> preprocessSolutions(Collection<Node> allSolutions, HintConfig config,
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
					double cost = NodeAlignment.getSubCostEsitmate(
							deleted, insertion.pair, config, getDistanceMeasure(config));
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

	private static String[] toArray(List<String> items) {
		return items.toArray(new String[items.size()]);
	}

	public List<EditHint> highlightStringEdit(Node node) {
		String[] nodeSeq = node.depthFirstIteration();

		double minDis = Double.MAX_VALUE;
		Node best = null;
		for (Node solution : getSolutions()) {
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

	public List<Node> getSolutions() {
		return solutions;
	}
}
