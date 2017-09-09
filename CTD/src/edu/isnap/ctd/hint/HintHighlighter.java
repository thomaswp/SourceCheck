package edu.isnap.ctd.hint;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.edit.Deletion;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.hint.edit.Insertion;
import edu.isnap.ctd.hint.edit.Reorder;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.Cast;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.DistanceMeasure;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.ctd.util.NodeAlignment.ProgressDistanceMeasure;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.ctd.util.map.MapFactory;

public class HintHighlighter {

	public static enum Highlight {
		Good, Add, Delete, Order, Move, Replaced
	}

	public PrintStream trace = System.out;

	private final List<Node> solutions;
	private final HintConfig config;
	private final RuleSet ruleSet;

	public HintHighlighter(HintMap hintMap) {
		this(hintMap.solutions, hintMap.ruleSet, hintMap.config);
	}

	public HintHighlighter(List<Node> solutions, HintConfig config) {
		this(solutions, null, config);
	}

	public HintHighlighter(List<Node> solutions, RuleSet ruleSet, HintConfig config) {
		this.solutions = config.preprocessSolutions ?
				preprocessSolutions(solutions, config) : solutions;
		this.ruleSet = ruleSet;
		this.config = config;
	}

	public List<EditHint> highlight(Node node) {
		BiMap<Node, Node> mapping = findSolutionMapping(node);
		return highlight(node, mapping);
	}

	public List<EditHint> highlight(Node node, final BiMap<Node, Node> mapping) {
		return highlight(node, mapping, true);
	}

	public List<EditHint> highlight(Node node, final BiMap<Node, Node> mapping,
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

							Insertion insertion = new Insertion(moveParent, pair, insertIndex);
							insertion.candidate = node;
							// If this is a code element parent, inserting the node should replace
							// the current node at this index
							if (isCodeElement(moveParent) &&
									insertIndex < moveParent.children.size()) {
								insertion.replaced = moveParent.children.get(insertIndex);
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

					// For clarity, this routine handles reorders for nodes that _are_ children
					// of a paired code element node (first), then it handles reorders for the
					// _children of_ non-code-element nodes (second)

					// Argument nodes are easy, since their order should be the same as their pair
					if (node.parent != null && node.parent == mapping.getTo(pair.parent) &&
							isCodeElement(node.parent)) {

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
					if (isCodeElement(node)) return;

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
			}
		});

		// Insertions: This time, iterate over the nodes in the pair
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
							if (isCodeElement(node)) {
								// The insert index algorithm could have gone out of bounds for
								// a code element's children (args)

								// For code elements, we cannot insert past the parent's size
								// (e.g. add an element to a list)
								// TODO: This should be supported eventually
								if (insertIndex >= node.children.size()) {
									break;
								}
								insertIndex = Math.max(0, insertIndex);
							}
							Insertion insertion = new Insertion(node, child, insertIndex);
							edits.add(insertion);
							// If the node is being inserted in a code element then it replaces
							// whatever already exists at this index in the parent
							// It's possible that a list/custom block will have 0 args, in which
							// case there will be no replacement.
							if (isCodeElement(node) && node.children.size() > 0) {
								insertion.replaced = node.children.get(insertIndex);
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

		if (reuseDeletedBlocks) {
			handleInsertionsAndMoves(colors, insertions, edits, mapping);
		}

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

	private void addScriptReplacement(Insertion insertion, BiMap<Node, Node> mapping,
			List<EditHint> edits, Map<Node, Highlight> colors) {

		// To be eligible for a script replacement, an insertion must have no replacement and have a
		// script parent with a child at the insertion index
		if (insertion.replaced != null || !insertion.parent.hasType(config.script) ||
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
			if (n1.hasType(config.literal)) continue;
			for (Node n2 : pairChildren) {
				if (mapping.getFrom(n1) == n2) {
					matches++;
					if (n2.parent == insertion.pair ||
							(n2.parentHasType(config.script) && n2.parent == insertion.pair)) {
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

	// Code elements (blocks) are nodes which are not themselves scripts but have an ancestor
	// which is a script. This precludes snapshots, sprites, custom blocks, variables, etc,
	// while including blocks and lists
	private boolean isCodeElement(Node node) {
		return config.isCodeElement(node);
	}

	public static DistanceMeasure getDistanceMeasure(HintConfig config) {
		return new ProgressDistanceMeasure(config);
	}

	private Mapping findSolutionMapping(Node node) {
		DistanceMeasure dm = getDistanceMeasure(config);
		long startTime = System.currentTimeMillis();
		List<Node> filteredSolutions = solutions;
		if (ruleSet != null) {
			RuleSet.trace = trace;
			filteredSolutions = ruleSet.filterSolutions(solutions, node);
		}
		Mapping bestMatch = NodeAlignment.findBestMatch(node, filteredSolutions, dm, config);
		if (bestMatch == null) throw new RuntimeException("No matches!");

		if (trace != null) {
			trace.println("------------------------------");
//			trace.println(node.prettyPrint());
//			trace.println("++++++++++");
			trace.println("Time to match: " + (System.currentTimeMillis() - startTime));
			trace.println(bestMatch.prettyPrint(true));
			trace.println(bestMatch.itemizedCost());
			bestMatch.printValueMappings(trace);
		}

		return bestMatch;
	}


	/**
	 * Remove side scripts from the submitted solutions. We do this to prevent side-script matches
	 * from have too much influence in the matching process.
	 */
	private static List<Node> preprocessSolutions(List<Node> allSolutions,
			final HintConfig config) {

		// TODO: This doesn't work well with multi-script and multi-sprite solutions

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
//							trace.println("Preprocess removed: " + node.children.get(i));
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

			// We're only interested in candidate nodes that are in scripts. This ignores things
			// like variables, sprites and custom block definitions
			if (!deleted.hasAncestor(new Node.TypePredicate(config.script))) continue;

			// If a deletion has been changed in the loop (e.g. to a move), ignore it
			if (colors.get(deleted) != Highlight.Delete) continue;

			Insertion bestMoveMatch = null;
			double bestMoveCost = Double.MAX_VALUE;

			for (Insertion insertion : insertions) {
				// We're also only interested in working with insertions into scripts
				if (!insertion.parent.hasAncestor(new Node.TypePredicate(config.script))) continue;

				boolean matchParent = deleted.parent == insertion.parent;
				boolean sameType = deleted.hasType(insertion.type);

				// If it's an insertion/deletion of the same type in the same script parent, it's
				// just a reorder
				if (sameType && matchParent && insertion.parent.hasType(config.script) &&
						insertion.index != deleted.index()) {
					colors.put(deleted, Highlight.Move);
					Reorder reorder = new Reorder(deleted, insertion.index,
							isCodeElement(deleted.parent));
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
				if (sameType && insertion.candidate == null &&
						!insertion.type.equals(config.literal)) {
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

		BiMap<Node, Node> mapping = new BiMap<>(MapFactory.IdentityHashMapFactory);
		for (int[] a : alignPairs) {
			if (a[0] == -1 || a[1] == -1) continue;
			Node from = node.nthDepthFirstNode(a[0]);
			Node to = best.nthDepthFirstNode(a[1]);
			mapping.put(from, to);
		}

		return highlight(node, mapping);
	}
}
