package edu.isnap.eval.agreement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.eval.agreement.TutorEdits.Priority;
import edu.isnap.eval.agreement.TutorEdits.TutorEdit;
import edu.isnap.eval.agreement.TutorEdits.Validity;

public class RateHints {

	public static void rate(GoldStandard standard, HintSet hintSet) {
		for (String assignment : standard.getAssignments()) {
			System.out.println("----- " + assignment + " -----");

			ListMap<Integer, HintRating> ratings = new ListMap<>();
			double totalWeightedValidity = 0, totalWeightedPriority = 0;
			for (Integer snapshotID : standard.getSnapshotIDs(assignment)) {
				List<TutorEdit> validEdits = standard.getValidEdits(assignment, snapshotID);
				List<HintOutcome> hints = hintSet.get(snapshotID);

				// TODO: do both this and not this
//				double maxWeight = hints.stream().mapToDouble(h -> h.weight).max().orElse(0);
//				hints = hints.stream().
//						filter(h -> h.weight == maxWeight).
//						collect(Collectors.toList());

				if (hints == null || hints.size() == 0) continue;

				for (HintOutcome hint : hints) {
					HintRating rating = new HintRating(hint);
					TutorEdit exactMatch = findMatchingEdit(validEdits, hint);

					rating.validity = Validity.Invalid;
					if (exactMatch != null) {
						rating.validity = Validity.Valid;
						rating.priority = exactMatch.priority;
					}
//					System.out.println(rating);
					ratings.add(snapshotID, rating);
				}
				List<HintRating> snapshotRatings = ratings.get(snapshotID);
				double totalWeight = snapshotRatings.stream().mapToDouble(r -> r.hint.weight).sum();
				double validity = snapshotRatings.stream()
						.mapToDouble(r -> r.hint.weight * r.validity.value)
						.sum() / totalWeight;
				// TODO: figure out whether to count 0-priority items
				double priority = snapshotRatings.stream()
						.filter(r -> r.priority != null)
						.mapToDouble(r -> r.hint.weight * r.priority.points())
						.sum() / totalWeight;

				totalWeightedValidity += validity;
				totalWeightedPriority += priority;

				System.out.printf("%d: %.03fv / %.03fp\n", snapshotID, validity, priority);
			}

			int nSnapshots = ratings.size();
			System.out.printf("TOTAL: %.03fv / %.03fp\n",
					totalWeightedValidity / nSnapshots, totalWeightedPriority / nSnapshots);
		}
	}

	public static Node normalizeNewValues(Node from, Node to) {
		// We don't differentiate values by type, since multiple types can share values (e.g.
		// varDecs and vars)
		Set<String> usedValues = new HashSet<>();
		from.recurse(node -> usedValues.add(node.value));

		to = to.copy();
		// Create a list of nodes before iteration, since we'll be modifying children
		List<Node> toNodes = new ArrayList<>();
		to.recurse(node -> toNodes.add(node));
		for (Node node : toNodes) {
			if (node.index() == -1) continue;
			// If this node has a non-generated ID but has no match in from (or it's been
			// relabeled and has a new type), it's a new node, so prune its children
			if (node.id != null && !node.id.startsWith(Agreement.GEN_ID_PREFIX)) {
				Node fromMatch = from.searchForNodeWithID(node.id);
				if (fromMatch == null || !fromMatch.hasType(node.type())) {
					Agreement.pruneImmediateChildren(node);
				}
			}
			if (node.value != null && !usedValues.contains(node.value)) {
				Node parent = node.parent;
				Node replacement = new Node(parent, node.type(), "[NEW_VALUE]", node.id);
				int index = node.index();
				parent.children.remove(index);
				parent.children.add(index, replacement);
				replacement.children.addAll(node.children);
				node.children.clear();
			}
		}
		return to;
	}

	public static TutorEdit findMatchingEdit(List<TutorEdit> validEdits, HintOutcome outcome) {
		if (validEdits.isEmpty()) return null;
		// TODO: supersets, subsets of edits
		Node outcomeNode = normalizeNewValues(validEdits.get(0).from, outcome.outcome);
		for (TutorEdit tutorEdit : validEdits) {
			Node tutorOutcomeNode = normalizeNewValues(tutorEdit.from, tutorEdit.to);
			if (outcomeNode.equals(tutorOutcomeNode)) return tutorEdit;
			else if (outcome.outcome.equals(tutorEdit.to)) {
				System.out.println(Diff.diff(tutorEdit.from.prettyPrint(true),
						tutorEdit.to.prettyPrint(true)));
				System.out.println(Diff.diff(tutorOutcomeNode.prettyPrint(true),
						outcomeNode.prettyPrint(true), 2));
				throw new RuntimeException("Normalized nodes should be equal if nodes are equal!");
			}
		}
		return null;
	}

	public static class HintSet extends ListMap<Integer, HintOutcome> {
		public final String name;

		public HintSet(String name) {
			this.name = name;
		}
	}

	public static class HintOutcome {
		public final Node outcome;
		public final int snapshotID;
		public final double weight;
		public final List<EditHint> edits;

		public HintOutcome(Node outcome, int snapshotID, double weight, List<EditHint> edits) {
			this.outcome = outcome;
			this.snapshotID = snapshotID;
			this.weight = weight;
			this.edits = edits;
			if (weight <= 0) {
				throw new IllegalArgumentException("All weights must be positive");
			}
		}

		@Override
		public String toString() {
			return snapshotID + ":\n" + TutorEdit.editsToString(edits, false);
		}
	}

	public static class HintRating {
		public final HintOutcome hint;

		public Validity validity;
		public Priority priority;

		public HintRating(HintOutcome hint) {
			this.hint = hint;
		}

		@Override
		public String toString() {
			return validity + " / " + priority + "\n" + hint;
		}
	}

	public static class GoldStandard {

		private final HashMap<String, ListMap<Integer, TutorEdit>> map = new HashMap<>();

		public Set<String> getAssignments() {
			return map.keySet();
		}

		public Set<Integer> getSnapshotIDs(String assignment) {
			return map.get(assignment).keySet();
		}

		public List<TutorEdit> getValidEdits(String assignment, int snapshotID) {
			return map.get(assignment).getList(snapshotID);
		}

		public GoldStandard(ListMap<String, TutorEdit> consensusEdits) {
			for (String assignment : consensusEdits.keySet()) {
				List<TutorEdit> list = consensusEdits.get(assignment);
				ListMap<Integer, TutorEdit> snapshotMap = new ListMap<>();
				list.forEach(edit -> snapshotMap.add(edit.rowID, edit));
				map.put(assignment, snapshotMap);
			}
		}

	}
}
