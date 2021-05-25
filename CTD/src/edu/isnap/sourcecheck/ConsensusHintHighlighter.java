package edu.isnap.sourcecheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.isnap.hint.HintData;
import edu.isnap.hint.util.Cast;
import edu.isnap.hint.util.Tuple;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.Deletion;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.sourcecheck.edit.Reorder;
import edu.isnap.util.map.CountMap;

/**
 * Experiment from 2017 to improve hints by looking for consensus. Did not go very far, but code
 * is preserved for reference.
 */
public class ConsensusHintHighlighter extends HintHighlighter {

	public ConsensusHintHighlighter(HintData hintData) {
		super(hintData);
	}

	public List<EditHint> highlightConsensus(Node node) {

		// TODO: include in HintConfig
		int nMatches = 10;
		double consensusThreshold = 0.6;

		List<Mapping> matches = NodeAlignment.findBestMatches(
				node, getSolutions(), getDistanceMeasure(config), config, nMatches);

		final int n = matches.size();
		final int thresh = (int) Math.ceil(n * consensusThreshold);

		System.out.println(thresh + "/" + n);
		for (Mapping mapping : matches) System.out.println(mapping.prettyPrint(false));

		final Map<Node, List<NodeEdits>> editsMap = new HashMap<>();

		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				ArrayList<NodeEdits> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) list.add(new NodeEdits(node));
				editsMap.put(node, list);
			}
		});

		for (int i = 0; i < n; i++) {
			List<EditHint> edits = highlight(node, matches.get(i));
			addNodeEdits(editsMap, edits, i);
		}

		final List<Insertion> insertions = new LinkedList<>();
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				List<NodeEdits> votes = editsMap.get(node);
				CountMap<Tuple<String, Integer>> insertionVotes = new CountMap<>();
				CountMap<Tuple<String, Integer>> shouldReplace = new CountMap<>();
				for (NodeEdits vote : votes) {
					Set<Tuple<String, Integer>> additions = new HashSet<>();
					for (EditHint hint : vote.additions) {
						String type;
						int index;
						boolean replace;

						// Hint should be a reorder or an Insertion
						if (hint instanceof Reorder) {
							Reorder reorder = (Reorder) hint;
							type = reorder.node.type();
							index = reorder.index;
							replace = reorder.inPlace;
						} else {
							Insertion insertion = (Insertion) hint;
							type = insertion.type;
							index = insertion.index;
							replace = insertion.replaced != null;
						}

						Tuple<String, Integer> key = new Tuple<>(type, index);
						// Don't allow duplicate additions from one voter
						if (!additions.add(key)) continue;
						insertionVotes.change(key, 1);
						if (replace) shouldReplace.change(key, 1);
					}
				}

				for (Tuple<String, Integer> addition : insertionVotes.keySet()) {
					int count = insertionVotes.getCount(addition);
					if (count >= thresh) {
						Insertion insertion = new Insertion(
								node, node.constructNode(null, addition.x), addition.y, null);
						if (shouldReplace.getCount(addition) * 2 > count) {
							insertion.replaced = node.children.get(insertion.index);
						}
						insertions.add(insertion);
					}
				}

				// TODO: should we suggest inserts without an index if there are enough votes?
			}
		});

		final List<EditHint> hints = new LinkedList<>();
		hints.addAll(insertions);

		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				List<NodeEdits> votes = editsMap.get(node);

				if (NodeEdits.shouldDelete(votes, thresh)) {
					// Ignore deletions which have already been implied by a replacement
					for (Insertion insertion : insertions) {
						if (insertion.replaced == node) return;
					}
					hints.add(new Deletion(node));
					return;
				}

				if (NodeEdits.shouldMoveOrDelete(votes, thresh)) {
					CountMap<Tuple<Node, Integer>> moveVotes = new CountMap<>();
					for (NodeEdits edits : votes) {
						if (edits.move != null) {
							Node parent = edits.move.parent;
							int index;
							// Should only be able to be a Reorder or Insertion
							if (edits.move instanceof Reorder) {
								index = ((Reorder) edits.move).index;
							} else {
								index = ((Insertion) edits.move).index;
							}
							moveVotes.change(new Tuple<>(parent, index), 1);
						}
					}
					Insertion insertion = null;
					for (Tuple<Node, Integer> addition : moveVotes.keySet()) {
						// If we have consensus over where to move the node...
						if (moveVotes.getCount(addition) >= thresh) {
							// First look for an existing insertion that we could use
							for (Insertion existingInsertion : insertions) {
								if (existingInsertion.parent == addition.x &&
										existingInsertion.index == addition.y) {
									insertion = existingInsertion;
									break;
								}
							}
							// Otherwise create a new one
							if (insertion == null) {
								insertion = new Insertion(addition.x, node, addition.y, null);
								hints.add(insertion);
							}
							break;
						}
					}
					// If we did not reach consensus on where to move the node, put it as the
					// candidate for a an "missingParent" insertion
					if (insertion == null) {
						insertion = new Insertion(node.constructNode(null, "placeholder"), node, 0,
								null, true);
						hints.add(insertion);
					}
					insertion.candidate = node;
				}

			}
		});

		// We have not added any reorders up until this point. A reorder is just an insertion where
		// the candidate has the same parent as the insertion, so we replace any such insertions
		// with reorders. The client handles them very similarly, but there are no insert indicators
		// for reorders, just yellow highlights and hover hints
		for (int i = 0; i < hints.size(); i++) {
			Insertion insertion = Cast.cast(hints.get(i), Insertion.class);
			if (insertion != null && insertion.candidate != null) {
				if (insertion.candidate.parent == insertion.parent) {
					Reorder reorder = new Reorder(insertion.candidate, insertion.index,
							isCodeElement(insertion.parent));
					hints.remove(i);
					hints.add(i, reorder);
				}
			}
		}

		Collections.sort(hints);

		return hints;
	}

	private static class NodeEdits {
		public final Node node;
		public final List<EditHint> additions = new ArrayList<>();
		private EditHint move;
		private boolean delete;

		public NodeEdits(Node node) {
			this.node = node;
		}

		public void setMove(EditHint move) {
			if (node.hasType("literal")) {
				System.out.print("");
			}
			if (this.move == null) this.move = move;
		}

		public void addDelete() {
			if (move == null) this.delete = true;
		}

		public static boolean shouldMoveOrDelete(List<NodeEdits> votes, int thresh) {
			int yay = 0;
			for (NodeEdits edits : votes) {
				if (edits.delete || edits.move != null) yay++;
			}
			return yay >= thresh;
		}

		public static boolean shouldDelete(List<NodeEdits> votes, int thresh) {
			int yay = 0;
			for (NodeEdits edits : votes) {
				if (edits.delete) yay++;
			}
			return yay >= thresh;
		}
	}

	private static void addNodeEdits(Map<Node, List<NodeEdits>> editsMap,
			List<EditHint> edits, int index) {
		for (EditHint edit : edits) {
			if (edit instanceof Deletion) {
				editsMap.get(((Deletion) edit).node).get(index).addDelete();
			} else if (edit instanceof Reorder) {
				Reorder reorder = (Reorder) edit;
				editsMap.get(reorder.node).get(index).setMove(edit);
				editsMap.get(edit.parent).get(index).additions.add(reorder);
			} if (edit instanceof Insertion) {
				Insertion insertion = (Insertion) edit;
				if (insertion.candidate != null) {
					editsMap.get(insertion.candidate).get(index).setMove(insertion);
				}
				if (insertion.replaced != null) {
					editsMap.get(insertion.replaced).get(index).addDelete();
				}
				if (!insertion.missingParent) {
					editsMap.get(insertion.parent).get(index).additions.add(insertion);
				}
			}
		}
	}

	// Code elements (blocks) are nodes which are not themselves scripts but have an ancestor
	// which is a script. This precludes snapshots, sprites, custom blocks, variables, etc,
	// while including blocks and lists
	@SuppressWarnings("deprecation")
	private final boolean isCodeElement(Node node) {
		return node != null && !config.isScript(node.type()) &&
				node.hasAncestor(n -> config.isScript(node.type()));
	}

}
