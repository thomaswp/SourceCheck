package edu.isnap.sourcecheck.priority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.sourcecheck.priority.Ordering.Addition;
import edu.isnap.util.map.CountMap;

public class HintPrioritizer {

	public final HintData hintData;

	private final HintHighlighter highlighter;
	private final HintConfig config;

	public HintPrioritizer(HintHighlighter highlighter) {
		this.highlighter = highlighter;
		this.hintData = highlighter.hintData;
		this.config = hintData.config;
	}

	public void assignPriorities(Mapping bestMatch, List<EditHint> hints) {
		Node node = bestMatch.from;
		// TODO: why completely recalculate this?
		List<Mapping> bestMatches = highlighter.findBestMappings(node, config.votingK);

		if (!bestMatches.stream().anyMatch(m -> m.to == bestMatch.to)) {
			throw new IllegalArgumentException("bestMatch must be a member of hints");
		}

		// Get counts of how many times each edit appears in the top matches
		CountMap<EditHint> hintCounts = new CountMap<>();
		bestMatches.stream()
		// Use a set so duplicates from a single target solution don't get double-counted
		.map(m -> new HashSet<>(highlighter.highlight(node, m)))
		.forEach(set -> hintCounts.incrementAll(set));

		for (EditHint hint : hints) {
			Priority priority = new Priority();
			priority.consensusNumerator = hintCounts.getCount(hint);
			priority.consensusDenominator = bestMatches.size();
			// Numerators should be less than demoninators, and if there's only one best match,
			// it should always match itself
			if (priority.consensusNumerator > priority.consensusDenominator ||
					(priority.consensusNumerator == 1 && priority.consensusNumerator != 1)) {
				throw new RuntimeException("Inconsistent EditHint equality.");
			}
			hint.priority = priority;
		}

		// Currently these do not activate because the highlighter's DataConsumer does not request
		// the required DataModels. Additionally, they are unmaintained and may be buggy.
		findPlacementTimes(bestMatch, hints, bestMatches);
		findOrderingPriority(hints, node, bestMatch);
	}

	@SuppressWarnings("unlikely-arg-type")
	private void findPlacementTimes(Mapping bestMatch, List<EditHint> hints, List<Mapping> bestMatches) {
		PlacementTimesModel placementTimesModel = hintData.getModel(PlacementTimesModel.class);
		if (placementTimesModel == null) return;

		// TODO: This was changed to a map with a String (not Node) key, so it is broken.
		// Also, remember that each solution may have multiple duplicate submissions (CountMap)
		Map<String, Map<String, Double>> nodePlacementTimes =
				placementTimesModel.nodePlacementTimes;
		Map<Mapping, Mapping> bestToGoodMappings = new HashMap<>();
		if (nodePlacementTimes != null && nodePlacementTimes.containsKey(bestMatch.to)) {
			for (Mapping match : bestMatches) {
				if (bestMatch.to == match.to) continue;
				Map<String, Double> creationPercs = nodePlacementTimes.get(match.to);
				if (creationPercs == null) continue;
				Mapping mapping = new NodeAlignment(bestMatch.to, match.to, config)
						.calculateMapping(highlighter.getDistanceMeasure());
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
	}

	private void findOrderingPriority(List<EditHint> hints, Node node, Mapping bestMatch) {
		OrderingModel orderMatrix = hintData.getModel(OrderingModel.class);
		if (orderMatrix == null) return;

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

		for (Insertion insertion : insertions) {
			if (!insertionAdditions.containsKey(insertion)) continue;

			List<Addition> additions = orderMatrix.additions();
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
				double percOrdered = orderMatrix.getPercOrdered(i, insertIndex);
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

		calculateOrderingRanks(insertions, insertionAdditions, orderMatrix);
	}

	private void calculateOrderingRanks(List<Insertion> insertions,
			Map<Insertion, Addition> insertionMap, OrderingModel orderMatrix) {
		List<Addition> additions = orderMatrix.additions();
		List<Insertion> orderedInsertions = insertionMap.keySet().stream()
				.filter(insertion -> additions.contains(insertionMap.get(insertion)))
				.collect(Collectors.toList());
		Collections.sort(orderedInsertions, (a, b) -> {
			Addition additionA = insertionMap.get(a);
			Addition additionB = insertionMap.get(b);
			// TODO: I think this may be able to produce inconsistent orderings
			return orderMatrix.getPercOrdered(
					additions.indexOf(additionA), additions.indexOf(additionB)) >= 0.50 ?
							-1 : 1;
		});
		for (int i = 0; i < orderedInsertions.size(); i++) {
			Priority priority = orderedInsertions.get(i).priority;
			priority.orderingNumerator = i + 1;
			priority.orderingDenominator = orderedInsertions.size();

		}
	}
}
