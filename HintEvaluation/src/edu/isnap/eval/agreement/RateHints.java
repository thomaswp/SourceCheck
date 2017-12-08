package edu.isnap.eval.agreement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import edu.isnap.ctd.graph.Node;
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
			double totalWeightedPriority = 0;
			double[] totalWeightedValidity = new double[3];
			for (Integer snapshotID : standard.getSnapshotIDs(assignment)) {
				List<TutorEdit> validEdits = standard.getValidEdits(assignment, snapshotID);
				if (validEdits.size() == 0) continue;
				List<HintOutcome> hints = hintSet.get(snapshotID);

//				if (snapshotID == 145234) {
//					validEdits.forEach(e -> System.out.println( e.to.prettyPrint(true)));
//					System.out.println("!");
//				}

				// TODO: do both this and not this
//				double maxWeight = hints.stream().mapToDouble(h -> h.weight).max().orElse(0);
//				hints = hints.stream().
//						filter(h -> h.weight == maxWeight).
//						collect(Collectors.toList());

				if (hints == null || hints.size() == 0) continue;

				double[] weightedValidity = new double[3];

				for (HintOutcome hint : hints) {
//					if (hints.size() > 8 && hint == hints.get(8)) {
//						System.out.println("!!");
//					}
					HintRating rating = new HintRating(hint);
					TutorEdit exactMatch = findMatchingEdit(validEdits, hint, hintSet.config);

					rating.validity = Validity.NoTutors;
					if (exactMatch != null) {
						rating.validity = exactMatch.validity;
						rating.priority = exactMatch.priority;
						for (int i = 0; i < exactMatch.validity.value; i++) {
							weightedValidity[i] += hint.weight;
						}
					}
//					System.out.println(rating);
					ratings.add(snapshotID, rating);
				}
				List<HintRating> snapshotRatings = ratings.get(snapshotID);
				double weight = snapshotRatings.stream().mapToDouble(r -> r.hint.weight).sum();
				// TODO: figure out whether to count 0-priority items
				double priority = snapshotRatings.stream()
						.filter(r -> r.priority != null)
						.mapToDouble(r -> r.hint.weight * r.priority.points())
						.sum() / weight;

				for (int i = 0; i < weightedValidity.length; i++) {
					weightedValidity[i] /= weight;
					totalWeightedValidity[i] += weightedValidity[i];
				}
				totalWeightedPriority += priority;

				System.out.printf("%d: [%.03f, %.03f, %.03f]v / %.03fp\n", snapshotID,
						weightedValidity[0], weightedValidity[1], weightedValidity[2],
						priority);
			}

			int nSnapshots = ratings.size();
			for (int i = 0; i < totalWeightedValidity.length; i++) {
				totalWeightedValidity[i] /= nSnapshots;
			}
			System.out.printf("TOTAL: [%.03f, %.03f, %.03f]v / %.03fp\n",
					totalWeightedValidity[0], totalWeightedValidity[1], totalWeightedValidity[2],
					totalWeightedPriority / nSnapshots);
		}
	}

	private static Node pruneImmediateChildren(Node node, Predicate<String> condition) {
		for (int i = 0; i < node.children.size(); i++) {
			Node child = node.children.get(i);
			if (condition.test(child.type())) {
				pruneImmediateChildren(child, condition);
				if (child.children.isEmpty()) {
					node.children.remove(i--);
				}
			}
		}
		return node;
	}

	public static Node normalizeNewValuesTo(Node from, Node to, RatingConfig config,
			boolean prune) {
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
			if (prune && node.id != null && !node.id.startsWith(Agreement.GEN_ID_PREFIX)) {
				Node fromMatch = from.searchForNodeWithID(node.id);
				if (fromMatch == null || !fromMatch.hasType(node.type())) {
					pruneImmediateChildren(node, config::trimIfParentIsAdded);
				}
			}
			if (node.value != null && !usedValues.contains(node.value)) {
				boolean trim = true;
				if (config.useSpecificNumericLiterals()) {
					try {
						Double.parseDouble(node.value);
						trim = false;
					} catch (NumberFormatException e) { }
				}
				if (trim) {
					Node parent = node.parent;
					Node replacement = node.constructNode(
							parent, node.type(), "[NEW_VALUE]", node.id);
					int index = node.index();
					parent.children.remove(index);
					parent.children.add(index, replacement);
					replacement.children.addAll(node.children);
					node.children.clear();
				}
			}
			if (prune && node.children.size() == 0 && config.trimIfChildless(node.type())) {
				node.parent.children.remove(node.index());
			}
		}
		return to;
	}

	public static TutorEdit findMatchingEdit(List<TutorEdit> validEdits, HintOutcome outcome,
			RatingConfig config) {
		if (validEdits.isEmpty()) return null;
		// TODO: supersets, subsets of edits
		Node outcomeNode = normalizeNewValuesTo(validEdits.get(0).from, outcome.outcome,
				config, true);
		for (TutorEdit tutorEdit : validEdits) {
			Node tutorOutcomeNode = normalizeNewValuesTo(tutorEdit.from, tutorEdit.to,
					config, true);
			if (outcomeNode.equals(tutorOutcomeNode)) return tutorEdit;

			if (outcome.outcome.equals(tutorEdit.to)) {
				System.out.println(Diff.diff(tutorEdit.from.prettyPrint(true),
						tutorEdit.to.prettyPrint(true)));
				System.out.println(Diff.diff(tutorOutcomeNode.prettyPrint(true),
						outcomeNode.prettyPrint(true), 2));
				throw new RuntimeException("Normalized nodes should be equal if nodes are equal!");
			}

//			Set<EditHint> correctEdits = new HashSet<>(tutorEdit.edits);
//			correctEdits.retainAll(outcome.edits);
//			if (correctEdits.size() > 0) {
//				System.out.println("Partially overlapping hints: " +
//						tutorEdit.rowID + " / " + tutorEdit.hintID);
//				System.out.println("Tutor:");
//				tutorEdit.edits.forEach(System.out::println);
//				System.out.println("Generated:");
//				outcome.edits.forEach(System.out::println);
//				System.out.println("Overlapping:");
//				correctEdits.forEach(System.out::println);
//				System.out.println("Difference (generated -> tutor):");
//				System.out.println(Diff.diff(outcome.outcome.prettyPrint(true),
//						tutorOutcomeNode.prettyPrint(true)));
//				System.out.println("--------------------");
//			}
		}
		return null;
	}

	public static class HintSet extends ListMap<Integer, HintOutcome> {
		public final String name;
		public final RatingConfig config;

		public HintSet(String name, RatingConfig config) {
			this.name = name;
			this.config = config;
		}
	}

	public static class HintOutcome {
		public final Node outcome;
		public final int snapshotID;
		public final double weight;

		public HintOutcome(Node outcome, int snapshotID, double weight) {
			this.outcome = outcome;
			this.snapshotID = snapshotID;
			this.weight = weight;
			if (weight <= 0 || Double.isNaN(weight)) {
				throw new IllegalArgumentException("All weights must be positive: " + weight);
			}
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
		private final List<HintRequest> hintRequests = new ArrayList<>();

		public Set<String> getAssignments() {
			return map.keySet();
		}

		public List<HintRequest> getHintRequests() {
			return Collections.unmodifiableList(hintRequests);
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
				list.forEach(edit -> snapshotMap.add(edit.requestID, edit));
				map.put(assignment, snapshotMap);

				Set<Integer> addedIDs = new HashSet<>();
				list.forEach(edit -> {
					if (addedIDs.add(edit.requestID)) {
						hintRequests.add(new HintRequest(edit.requestID, assignment, edit.from));
					}
				});
			}
		}
	}

	// TODO: Merge with or differentiate from other HintRequest class
	public static class HintRequest {
		public final int id;
		public final String assignmentID;
		public final Node code;

		public HintRequest(int id, String assignmentID, Node code) {
			this.id = id;
			this.assignmentID = assignmentID;
			this.code = code;
		}
	}

	public abstract static class RatingConfig {
		public abstract boolean useSpecificNumericLiterals();
		public abstract boolean trimIfChildless(String type);
		public abstract boolean trimIfParentIsAdded(String type);
	}
}
