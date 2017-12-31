package edu.isnap.rating;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.rating.EditExtractor.Edit;
import edu.isnap.rating.TutorHint.Priority;
import edu.isnap.rating.TutorHint.Validity;

public class RateHints {

	public final static String GS_SPREADSHEET = "gold-standard.csv";
	public final static String ALGORITHMS_DIR = "algorithms";
	public final static String TRAINING_DIR = "training";

	public final static String DATA_ROOT_DIR = "../data/hint-rating/";
	public final static String ISNAP_DATA_DIR = DATA_ROOT_DIR + "isnap2017/";
	public final static String ITAP_DATA_DIR = DATA_ROOT_DIR + "itap2016/";

	public static void main(String[] args) throws FileNotFoundException, IOException {
		rateDir(ISNAP_DATA_DIR, RatingConfig.Snap);
	}

	public static void rateDir(String path, RatingConfig config)
			throws FileNotFoundException, IOException {
		GoldStandard standard = GoldStandard.parseSpreadsheet(path + GS_SPREADSHEET);
		File algorithmsFolder = new File(path, ALGORITHMS_DIR);
		if (!algorithmsFolder.exists() || !algorithmsFolder.isDirectory()) {
			throw new RuntimeException("Missing algorithms folder");
		}
		for (File algorithmFolder : algorithmsFolder.listFiles(file -> file.isDirectory())) {
			HintSet hintSet = HintSet.fromFolder(algorithmFolder.getName(), config,
					algorithmFolder.getPath());
			System.out.println(hintSet.name);
			rate(standard, hintSet);
		}
	}

	public static void rate(GoldStandard standard, HintSet hintSet) {
		EditExtractor extractor = new EditExtractor(hintSet.config.areNodeIDsConsistent());
		for (String assignment : standard.getAssignmentIDs()) {
			System.out.println("----- " + assignment + " -----");

			ListMap<String, HintRating> ratings = new ListMap<>();
			double totalWeightedPriority = 0;
			double[] totalWeightedValidity = new double[3];
			for (String requestID : standard.getRequestIDs(assignment)) {
				List<TutorHint> validEdits = standard.getValidEdits(assignment, requestID);
				if (validEdits.size() == 0) continue;
				List<HintOutcome> hints = hintSet.getOutcomes(requestID);

//				if (snapshotID == 145234) {
//					validEdits.forEach(e -> System.out.println( e.to.prettyPrint(true)));
//					System.out.println("!");
//				}

				// TODO: do both this and not this
//				double maxWeight = hints.stream().mapToDouble(h -> h.weight).max().orElse(0);
//				hints = hints.stream().
//						filter(h -> h.weight == maxWeight).
//						collect(Collectors.toList());

				System.out.println(requestID + ": " + hints.size());
				if (hints == null || hints.size() == 0) continue;

				double[] weightedValidity = new double[3];

				for (HintOutcome hint : hints) {
//					if (hints.size() > 8 && hint == hints.get(8)) {
//						System.out.println("!!");
//					}
					HintRating rating = new HintRating(hint);
					TutorHint exactMatch = findMatchingEdit(validEdits, hint, hintSet.config,
							extractor);

					rating.validity = Validity.NoTutors;
					if (exactMatch != null) {
						rating.validity = exactMatch.validity;
						rating.priority = exactMatch.priority;
						for (int i = 0; i < exactMatch.validity.value; i++) {
							weightedValidity[i] += hint.weight();
						}
					}
//					System.out.println(rating);
					ratings.add(requestID, rating);
				}
				List<HintRating> snapshotRatings = ratings.get(requestID);
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

				System.out.printf("%s: [%.03f, %.03f, %.03f]v / %.03fp\n", requestID,
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

	public static TutorHint findMatchingEdit(List<TutorHint> validHints, HintOutcome outcome,
			RatingConfig config, EditExtractor extractor) {
		if (validHints.isEmpty()) return null;
		// TODO: supersets, subsets of edits
		ASTNode fromNode = validHints.get(0).from;
		ASTNode outcomeNode = normalizeNewValuesTo(fromNode, outcome.result,
				config, true);
		for (TutorHint tutorHint : validHints) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, tutorHint.to, config, true);
			if (outcomeNode.equals(tutorOutcomeNode)) return tutorHint;

			if (outcome.result.equals(tutorHint.to)) {
				System.out.println(Diff.diff(
						tutorHint.from.prettyPrint(true, config),
						tutorHint.to.prettyPrint(true, config)));
				System.out.println(Diff.diff(
						tutorOutcomeNode.prettyPrint(true, config),
						outcomeNode.prettyPrint(true, config), 2));
				throw new RuntimeException("Normalized nodes should be equal if nodes are equal!");
			}
		}

		outcomeNode = normalizeNewValuesTo(fromNode, outcome.result, config, false);
		Set<Edit> outcomeEdits = extractor.getEdits(fromNode, outcomeNode);
		Set<Edit> bestOverlap = new HashSet<>();
		TutorHint bestHint = null;
		// TODO: sort by validity and priority first
//		Collections.sort(validHints);
		for (TutorHint tutorHint : validHints) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, tutorHint.to, config, false);
			Set<Edit> tutorEdits = extractor.getEdits(fromNode, tutorOutcomeNode);
			Set<Edit> overlap = new HashSet<>(tutorEdits);
			overlap.retainAll(outcomeEdits);
			if (overlap.size() == outcomeEdits.size() || overlap.size() == tutorEdits.size()) {
				bestOverlap = overlap;
				bestHint = tutorHint;
				break;
			}
		}
		if (bestHint != null) {
//			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, bestHint.to, config, false);
//			System.out.println("Tutor Hint:");
//			System.out.println(Diff.diff(
//					fromNode.prettyPrint(true, config),
//					tutorOutcomeNode.prettyPrint(true, config)));
//			System.out.println("Alg Hint:");
//			System.out.println(Diff.diff(
//					fromNode.prettyPrint(true, config),
//					outcomeNode.prettyPrint(true, config), 2));
//			Set<Edit> tutorEdits = extractor.getEdits(bestHint.from, bestHint.to);
//			EditExtractor.printEditsComparison(tutorEdits, outcomeEdits, "Tutor Hint", "Alg Hint");
//			if (bestOverlap.size() == tutorEdits.size() &&
//					bestOverlap.size() == outcomeEdits.size() &&
//					bestOverlap.size() > 0) {
//				throw new RuntimeException("Edits should not match if hint outcomes did not!");
//			}
//			System.out.println("-------------------");
			// TODO: Treat this differently
			return bestHint;
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
}
