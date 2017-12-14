package edu.isnap.rating;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.rating.TutorEdit.Priority;
import edu.isnap.rating.TutorEdit.Validity;

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
				List<HintOutcome> hints = hintSet.getOutcomes(snapshotID);

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
							weightedValidity[i] += hint.weight();
						}
					}
//					System.out.println(rating);
					ratings.add(snapshotID, rating);
				}
				List<HintRating> snapshotRatings = ratings.get(snapshotID);
				double weight = snapshotRatings.stream().mapToDouble(r -> r.hint.weight()).sum();
				// TODO: figure out whether to count 0-priority items
				double priority = snapshotRatings.stream()
						.filter(r -> r.priority != null)
						.mapToDouble(r -> r.hint.weight() * r.priority.points())
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

	private static ASTNode pruneImmediateChildren(ASTNode node, Predicate<String> condition) {
		for (int i = 0; i < node.children().size(); i++) {
			ASTNode child = node.children().get(i);
			if (condition.test(child.type)) {
				pruneImmediateChildren(child, condition);
				if (child.children().isEmpty()) {
					node.removeChild(i--);
				}
			}
		}
		return node;
	}

	public static ASTNode normalizeNewValuesTo(ASTNode from, ASTNode to, RatingConfig config,
			boolean prune) {
		// We don't differentiate values by type, since multiple types can share values (e.g.
		// varDecs and vars)
		Set<String> usedValues = new HashSet<>();
		from.recurse(node -> usedValues.add(node.value));

		to = to.copy();
		// Create a list of nodes before iteration, since we'll be modifying children
		List<ASTNode> toNodes = new ArrayList<>();
		to.recurse(node -> toNodes.add(node));
		for (ASTNode node : toNodes) {
			if (node.parent() == null) continue;
			// If this node has a non-generated ID but has no match in from (or it's been
			// relabeled and has a new type), it's a new node, so prune its children
			// TODO: This doesn't make sense for Python, where IDs aren't consistent,
			// and right now it relies on snap-specific code
			if (prune && node.id != null) {
				ASTNode fromMatch = (ASTNode) from.search(n -> n != null &&
						StringUtils.equals(n.id(), node.id));
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
					ASTNode parent = node.parent();
					ASTNode replacement = new ASTNode(node.type, "[NEW_VALUE]", node.id);
					int index = node.index();
					parent.removeChild(index);
					parent.addChild(index, replacement);
					for (ASTNode child : node.children()) {
						replacement.addChild(child);
					}
					node.clearChildren();
				}
			}
			if (prune && node.children().size() == 0 && config.trimIfChildless(node.type())) {
				node.parent().removeChild(node.index());
			}
		}
		return to;
	}

	public static TutorEdit findMatchingEdit(List<TutorEdit> validEdits, HintOutcome outcome,
			RatingConfig config) {
		if (validEdits.isEmpty()) return null;
		// TODO: supersets, subsets of edits
		ASTNode outcomeNode = normalizeNewValuesTo(validEdits.get(0).from, outcome.outcome,
				config, true);
		for (TutorEdit tutorEdit : validEdits) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(tutorEdit.from, tutorEdit.to,
					config, true);
			if (outcomeNode.equals(tutorOutcomeNode)) return tutorEdit;

			if (outcome.outcome.equals(tutorEdit.to)) {
				System.out.println(Diff.diff(
						tutorEdit.from.prettyPrint(true, config),
						tutorEdit.to.prettyPrint(true, config)));
				System.out.println(Diff.diff(
						tutorOutcomeNode.prettyPrint(true, config),
						outcomeNode.prettyPrint(true, config), 2));
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

	public static interface RatingConfig {
		public boolean useSpecificNumericLiterals();
		public boolean trimIfChildless(String type);
		public boolean trimIfParentIsAdded(String type);
		public boolean nodeTypeHasBody(String type);
	}
}
