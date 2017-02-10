package edu.isnap.ctd.hint;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.ctd.graph.vector.IndexedVectorState;
import edu.isnap.ctd.graph.vector.VectorGraph;
import edu.isnap.ctd.graph.vector.VectorState;

public class HintGenerator {

	public final HintMap hintMap;

	public HintGenerator(HintMap hintMap) {
		this.hintMap = hintMap;
	}

	/**
	 * Gets the first hint generated for the given (root) Node, representing a student's current
	 * snapshot.
	 * @param node
	 * @return
	 */
	public synchronized VectorHint getFirstHint(Node node) {
		LinkedList<VectorHint> hints = new LinkedList<>();
		getHints(node, hints, 1);
		return hints.size() > 0 ? hints.getFirst() : null;
	}

	/**
	 * Gets all hints generated for the given (root) Node, representing a student's current
	 * snapshot.
	 * @param node
	 * @return
	 */
	public synchronized List<VectorHint> getHints(Node node) {
		LinkedList<VectorHint> hints = new LinkedList<>();
		getHints(node, hints, Integer.MAX_VALUE);
		postProcess(hints);
		return hints;
	}

	private void getHints(Node node, List<VectorHint> list, int limit) {

		VectorHint hint = getHint(node);

		// TODO: don't forget that really the skeleton need not match exactly,
		// we should just be matching as much as possible

		if (hint != null && !list.contains(hint)) {
			list.add(hint);
		}

		if (list.size() >= limit) return;
		for (Node child : node.children) getHints(child, list, limit);
	}

	public VectorState getGoalState(Node node) {
		return getGoalState(hintMap, node);
	}

	public static VectorState getGoalState(HintMap hintMap, Node node) {
		HintConfig config = hintMap.getHintConfig();

		VectorGraph graph = hintMap.getGraph(node);
		if (graph == null) return null;

		VectorState children = HintMap.getVectorState(node);
		IndexedVectorState context = hintMap.getContext(node);

		return graph.getGoalState(children, context, config);
	}

	private VectorHint getHint(Node node) {
		HintConfig config = hintMap.getHintConfig();

		VectorGraph graph = hintMap.getGraph(node);
		if (graph == null) return null;

		VectorState children = HintMap.getVectorState(node);
		IndexedVectorState context = hintMap.getContext(node);
		VectorState next = children;

		VectorState goal = graph.getGoalState(next, context, config);

		if (goal != null && config.straightToGoal.contains(node.type())) {
			next = goal;
		} else {
			// Get the best successor state from our current state
			VectorState hint = graph.getHint(next, context, config);
			// If we find a real hint, use it
			if (hint != null && !hint.equals(next)) {
				next = hint;
			}
			if (goal == null) goal = next;
		}

		double stayed = graph.getProportionStayed(children);
		boolean caution =
				graph.getGoalCount(children) >= config.pruneGoals &&
				stayed >= config.stayProportion;

		Node rootPath = HintMap.toRootPath(node).root();
		VectorHint hint = new VectorHint(node, rootPath.toString(), children, next, goal, caution);
		return hint;
	}

	private void postProcess(List<VectorHint> hints) {
		HintConfig config = hintMap.getHintConfig();

		// "Hash set" to compare by identity
		final Map<Node, Void> extraScripts = new IdentityHashMap<>();
		Map<VectorState, VectorHint> missingMap = new HashMap<>();

		// Find scripts that should be removed and don't give hints to their children
		for (VectorHint hint : hints) {
			int extraChildren = hint.from.countOf(config.script) -
					hint.goal.countOf(config.script);
			if (extraChildren > 0) {
				List<Integer> sizes = new LinkedList<>();
				for (Node child : hint.root.children) {
					if (child.hasType(config.script)) sizes.add(child.children.size());
				}
				Collections.sort(sizes);
				int cutoff = Integer.MAX_VALUE;
				// If all children are extra children, the cutoff size is infinite (above)
				// Otherwise, we get the nth smallest child and use that as the cutoff
				if (extraChildren < sizes.size()) {
					sizes.get(extraChildren);
				}
				// TODO: find a more comprehensive way of deciding on the primary script(s)
				for (Node child : hint.root.children) {
					if (child.hasType(config.script) && child.children.size() < cutoff) {
						extraScripts.put(child, null);
					}
				}
			}

		}

		// Remove the hints for the extra hints and make a map of missing blocks for the others
		List<VectorHint> toRemove = new LinkedList<>();
		for (VectorHint hint : hints) {
			if (hint.from.equals(hint.to) || hint.root.hasAncestor(new Predicate() {
				@Override
				public boolean eval(Node node) {
					return extraScripts.containsKey(node);
				}
			})) {
				toRemove.add(hint);
			} else {
				VectorState missingChildren = hint.getMissingChildren();
				// To benefit from a LinkHint, an existing hint must have some missing
				// children but it shouldn't be missing all of them (completely replaced)
				// unless it's empty to begin with, or a block hint
				if (missingChildren.length() > 0 && (
						!hint.root.hasType(config.script) ||
						hint.from.length() == 0 ||
						missingChildren.length() < hint.goal.length())) {
					missingMap.put(missingChildren, hint);
				}
			}
		}

		// Instead look for another hint that could use the blocks in these
		// scripts and make a LinkHint that points there
		List<VectorHint> toAdd = new LinkedList<>();
		for (VectorHint hint : hints) {
			if (extraScripts.containsKey(hint.root) &&
					hint.from.length() > 0) {

				VectorHint bestMatch = null;
				int bestUseful = 0;
				int bestDistance = Integer.MAX_VALUE;

				for (VectorState missing : missingMap.keySet()) {
					int useful = missing.overlap(hint.from);
					if (useful * config.linkUsefulRatio >= hint.from.length() &&
							useful >= bestUseful) {
						int distance = VectorState.distance(missing, hint.from);
						if (useful > bestUseful || distance < bestDistance) {
							bestUseful = useful;
							bestDistance = distance;
							bestMatch = missingMap.get(missing);
						}
					}
				}

				if (bestMatch != null) {
					// TODO: Why am I getting duplicate hints here (and likely elsewhere)?
					toAdd.add(new LinkHint(bestMatch, hint));
				}
			}
		}
		hints.addAll(toAdd);
		hints.removeAll(toRemove);
	}

}
