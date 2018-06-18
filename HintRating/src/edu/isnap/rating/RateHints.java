package edu.isnap.rating;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.TreeBag;
import org.apache.commons.lang.StringUtils;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.Diff.ColorStyle;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.EditExtractor.Deletion;
import edu.isnap.rating.EditExtractor.Edit;
import edu.isnap.rating.EditExtractor.Insertion;
import edu.isnap.rating.EditExtractor.Relabel;
import edu.isnap.rating.TutorHint.Priority;
import edu.isnap.rating.TutorHint.Validity;

public class RateHints {

	public final static String GS_SPREADSHEET = "gold-standard.csv";
	public final static String ALGORITHMS_DIR = "algorithms";
	public final static String OUTPUT_DIR = "ratings";
	public final static String TRAINING_FILE = "training.csv.gz";
	public static final String REQUEST_FILE = "requests.csv.gz";

	public final static String DATA_ROOT_DIR = "../data/hint-rating/";
	public final static String ISNAP_F16_S17_DATA_DIR = DATA_ROOT_DIR + "isnapF16-S17/";
	public final static String ISNAP_F16_F17_DATA_DIR = DATA_ROOT_DIR + "isnapF16-F17/";
	public final static String ITAP_S16_DATA_DIR = DATA_ROOT_DIR + "itapS16/";

	private final static String PARTIAL_UNSEEN_VALUE = "NEW_VALUE";

	public static void rateDir(String path, RatingConfig config, Validity targetValidity,
			boolean write) throws FileNotFoundException, IOException {
		GoldStandard standard = GoldStandard.parseSpreadsheet(path + GS_SPREADSHEET);
		File algorithmsFolder = new File(path, ALGORITHMS_DIR);
		if (!algorithmsFolder.exists() || !algorithmsFolder.isDirectory()) {
			throw new RuntimeException("Missing algorithms folder");
		}
		for (File algorithmFolder : algorithmsFolder.listFiles(file -> file.isDirectory())) {
			rateOneDir(path, algorithmFolder.getName(), config, standard, targetValidity, write,
					false);
		}
	}

	public static void rateOneDir(String parentDir, String dir, RatingConfig config,
			Validity targetValidity, boolean write, boolean debug)
					throws IOException, FileNotFoundException {
		GoldStandard standard = GoldStandard.parseSpreadsheet(parentDir + GS_SPREADSHEET);
		rateOneDir(parentDir, dir, config, standard, targetValidity, write, debug);
	}

	public static void rateOneDir(String parentDir, String dir, RatingConfig config,
			GoldStandard standard, Validity targetValidity, boolean write, boolean debug)
					throws IOException, FileNotFoundException {
		HintSet hintSet = HintSet.fromFolder(dir, config,
				String.format("%s/%s/%s", parentDir, ALGORITHMS_DIR, dir));
		System.out.println(hintSet.name);
		HintRatingSet ratings = rate(standard, hintSet, targetValidity, debug);
		if (write) {
			ratings.writeAllHints(String.format("%s/%s/%s/%s.csv",
					parentDir, RateHints.OUTPUT_DIR, targetValidity, dir));
		}
	}

	public static HintRatingSet rate(GoldStandard standard, HintSet hintSet,
			Validity targetValidity) {
		return rate(standard, hintSet, targetValidity, false);
	}

	public static HintRatingSet rate(GoldStandard standard, HintSet hintSet,
			Validity targetValidity, boolean debug) {
		RatingConfig config = hintSet.config;
		HintRatingSet ratingSet = new HintRatingSet(hintSet.name);
		EditExtractor extractor = new EditExtractor(config, ASTNode.EMPTY_TYPE);
		for (String assignmentID : standard.getAssignmentIDs()) {
			System.out.println("----- " + assignmentID + " -----");

			for (String requestID : standard.getRequestIDs(assignmentID)) {

				List<TutorHint> validHints = standard.getValidHints(assignmentID, requestID);

				// Remove any hints that don't match the required validity
				validHints.removeIf(hint -> !hint.validity.contains(targetValidity));

				// Make sure there is at least one hint with the required validity; otherwise,
				// assume there are no valid tutor hints and we should continue
				if (validHints.isEmpty()) continue;

				ASTNode fromNode = validHints.get(0).from;
				RequestRating requestRating = new RequestRating(requestID, assignmentID,
						fromNode, config);

				// Create an initial list of hints that are not matched to a tutor hint
				List<HintOutcome> unmatchedHints = new ArrayList<>(hintSet.getOutcomes(requestID));
				if (unmatchedHints.isEmpty()) {
					System.err.printf("No hints generated for request %s/%s.\n",
							assignmentID, requestID);
				}

				// First find full matches and remove any hints that match
				for (int i = 0; i < unmatchedHints.size(); i++) {
					HintOutcome hint = unmatchedHints.get(i);
					HintRating rating = findMatchingEdit(validHints, hint, extractor, config);
					if (rating != null) {
						requestRating.add(rating);
						unmatchedHints.remove(i--);
					}
				}
				// Then find any partial matches in the remaining hints
				for (HintOutcome hint : unmatchedHints) {
					HintRating partialRating = findPartiallyMatchingEdit(
							validHints, hint, config, extractor, true);
					requestRating.add(partialRating);
				}


				requestRating.forEach(rating -> rating.addEdits(fromNode, extractor, config));
				requestRating.sort();
				if (debug) {
					requestRating.printRatings(validHints.get(0).from, config, validHints);
				}
				ratingSet.add(requestRating);
				requestRating.printSummary();
			}

			ratingSet.printSummary(assignmentID);
		}
		return ratingSet;
	}

	private static ASTNode pruneAddedParent(ASTNode node, RatingConfig config) {
		for (int i = 0; i < node.children().size(); i++) {
			ASTNode child = node.children().get(i);
			if (config.trimIfParentIsAdded(child.type, child.value) && child.children().isEmpty()) {
				node.removeChild(i--);
			}
		}
		return node;
	}

	/**
	 * Prunes nodes from the given "to" AST based on the settings in the config.
	 * Note: For efficiency this modifies with given node - it does not return a copy.
	 */
	public static void pruneNewNodesTo(ASTNode from, ASTNode to, RatingConfig config) {
		// Create a list of nodes before iteration, since we'll be modifying children
		List<ASTNode> toNodes = new ArrayList<>();
		to.recurse(node -> toNodes.add(node));
		// Reverse the order so children are pruned before parents
		Collections.reverse(toNodes);

		// Remove nodes that should be pruned if childless
		for (ASTNode node : toNodes) {
			if (node.parent() == null) continue;
			// All empty-type nodes can be pruned, since they're just placeholders
			if (node.hasType(ASTNode.EMPTY_TYPE) ||
					// Also prune some nodes that have no meaning without children (e.g. scripts)
					(node.children().size() == 0 && config.trimIfChildless(node.type()))) {
				node.parent().removeChild(node.index());
			}
		}

		// Identify new nodes and prune their children.
		List<ASTNode> addedNodes = EditExtractor.getInsertedAndRenamedNodes(from, to);
		// Reverse sort by depth to prune children first
		addedNodes.sort(Comparator.comparing(node -> -node.depth()));
		for (ASTNode node : addedNodes) {
			if (node.parent() == null) continue;
			// If this node was created/changed in the hint, prune its immediate children for
			// nodes that are added automatically, according to the config, e.g. literal nodes
			// in Snap.
			pruneAddedParent(node, config);
		}
	}

	public static ASTNode normalizeNodeValues(ASTNode root, RatingConfig config) {
		root = root.copy();

		// Create a list of nodes before iteration, since we'll be modifying children
		List<ASTNode> nodes = new ArrayList<>();
		root.recurse(node -> nodes.add(node));

		// First check if any node values should be normalized, as specified in the config
		for (ASTNode node : nodes) {
			String normalizedValue = config.normalizeNodeValue(node.type, node.value);
			if (!StringUtils.equals(node.value, normalizedValue)) {
				// If so, replace the node's value with the normalized one
				node.replaceWith(new ASTNode(node.type, normalizedValue, node.id));
			}
		}

		return root;
	}

	public static ASTNode normalizeNewValuesTo(ASTNode normFrom, ASTNode to, RatingConfig config,
			String newValue) {
		to = normalizeNodeValues(to, config);

		// Get a set of all the node values used in the original AST. We don't differentiate values
		// by type, since multiple types can share values (e.g. varDecs and vars)
		Set<String> usedValues = new HashSet<>();
		normFrom.recurse(node -> usedValues.add(node.value));

		// Create a list of nodes before iteration, since we'll be modifying children
		List<ASTNode> toNodes = new ArrayList<>();
		to.recurse(node -> toNodes.add(node));

		for (ASTNode node : toNodes) {
			if (node.parent() == null) continue;

			if (node.value != null && !usedValues.contains(node.value)) {
				// If this node's value is new, we may normalize it
				boolean normalize = true;
				// First check if it's a new numeric literal and the config wants to normalize it
				if (config.useSpecificNumericLiterals()) {
					try {
						Double.parseDouble(node.value);
						normalize = false;
					} catch (NumberFormatException e) { }
				}
				if (normalize) {
					// If so, we replace its value with null, so all new values appear the same
					// NOTE: When considering partial matches, we replace the value with the
					// PARTIAL_UNSEEN_VALUE constant instead, but for full matching, it is important
					// the unspecified values can match new values.
					node.replaceWith(new ASTNode(node.type, newValue, node.id));
				}
			}
		}

		return to;
	}

	public static HintRating findMatchingEdit(List<TutorHint> validHints, HintOutcome outcome,
			EditExtractor extractor, RatingConfig config) {
		if (validHints.isEmpty()) return new HintRating(outcome);
		ASTNode fromNode = normalizeNodeValues(validHints.get(0).from, config);
		ASTNode outcomeNode = normalizeNewValuesTo(fromNode, outcome.result, config, null);
		pruneNewNodesTo(fromNode, outcomeNode, config);
		for (TutorHint tutorHint : validHints) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, tutorHint.to, config, null);
			pruneNewNodesTo(fromNode, tutorOutcomeNode, config);
			if (outcomeNode.equals(tutorOutcomeNode)) {
				return new HintRating(outcome, tutorHint, MatchType.Full);
			}

			if (outcome.result.equals(tutorHint.to)) {
				System.out.printf("Matching outcome hint (%d):\n", outcome.id);
				System.out.println(ASTNode.diff(fromNode, outcome.result, config));
				System.out.printf("Difference in normalized nodes (%d vs %d):\n",
						outcome.id, tutorHint.hintID);
				System.out.println(ASTNode.diff(tutorOutcomeNode, outcomeNode, config, 2));
				System.out.println("Tutor normalizing:");
				System.out.println(ASTNode.diff(tutorHint.to, tutorOutcomeNode, config, 2));
				System.out.println("Outcome normalizing:");
				System.out.println(ASTNode.diff(outcome.result, outcomeNode, config, 2));
				throw new RuntimeException("Normalized nodes should be equal if nodes are equal!");
			}
		}
		return null;
	}

	public static HintRating findPartiallyMatchingEdit(List<TutorHint> validHints,
			HintOutcome outcome, RatingConfig config, EditExtractor extractor,
			boolean errorOnFullMatch) {
		if (validHints.isEmpty()) return new HintRating(outcome);
		ASTNode fromNode = normalizeNodeValues(validHints.get(0).from, config);

		// Run again to get a version that's unpruned
		ASTNode outcomeNode = normalizeNewValuesTo(
				fromNode, outcome.result, config, PARTIAL_UNSEEN_VALUE);
		Bag<Edit> outcomeEdits = extractor.getEdits(fromNode, outcomeNode);
		if (outcomeEdits.size() == 0) return new HintRating(outcome);

		// There are three types of partial matches, only the last of which is detected here:
		// 1) The algorithm matches the hint perfectly in essence, but it misses some required
		//    elements of the AST. We treat these as exact matches, and attempt to detect them
		//    by pruning away less meaningful parts of the AST in findMatchingEdit().
		// 2) The algorithm creates 2 hints that together cover most of a tutor hint. We choose not
		//    to treat this as a special case, since all hints should be evaluated independently.
		//    However, with some hint interfaces (e.g. iSnap) they may not in fact be presented
		//    independently, so this is not a perfect solution.
		// 3) The algorithm matches part of the tutor hint (e.g. inserting something), but is
		//    missing other edits needed to make the hints complete, clear and not confusing.
		//    As long as the algorithm conveys a meaningful part of the tutor's hint, we call these
		//    partial matches. We define meaningful here as "not only deletions," but of course this
		//    is an imperfect definition.

		// Sort but then reverse, so highest priority hints come first
		Collections.sort(validHints);
		Collections.reverse(validHints);

		Bag<Edit> bestOverlap = new TreeBag<>();
		TutorHint bestHint = null;
		for (TutorHint tutorHint : validHints) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(
					fromNode, tutorHint.to, config, PARTIAL_UNSEEN_VALUE);
			Bag<Edit> tutorEdits = extractor.getEdits(fromNode, tutorOutcomeNode);
//			if (outcome.id == 524813201 && tutorHint.hintID == 10005) {
//				printPartialMatch(config, extractor, fromNode, outcomeNode, outcomeEdits, tutorHint, outcome);
//			}
			if (tutorEdits.size() == 0) continue;
			Bag<Edit> overlap = new TreeBag<>(tutorEdits);
			overlap.retainAll(outcomeEdits);
			if (overlap.size() > bestOverlap.size()) {
				if (errorOnFullMatch && overlap.size() == tutorEdits.size() &&
						overlap.size() == outcomeEdits.size()) {
					System.out.println("Tutor hint: ");
					System.out.println(ASTNode.diff(fromNode, tutorOutcomeNode, config));
					System.out.println("Alg hint: ");
					System.out.println(ASTNode.diff(fromNode, outcomeNode, config));
					EditExtractor.printEditsComparison(
							tutorEdits, outcomeEdits, "Tutor Hint", "Alg Hint");
					throw new RuntimeException("Edits should not match if hint outcomes did not!");
				}
				bestOverlap = overlap;
				bestHint = tutorHint;
			}
		}
		if (bestOverlap.size() == outcomeEdits.size()) {
			// If the overlap is only deletions, we do not count this as a partial match
			if (!bestOverlap.stream().allMatch(e -> e instanceof Deletion)) {
//				printPartialMatch(config, extractor, fromNode, outcomeNode, outcomeEdits, bestHint,
//						outcome);
				return new HintRating(outcome, bestHint, MatchType.Partial);
			}
		}
		return new HintRating(outcome);
	}

	protected static void printPartialMatch(RatingConfig config, EditExtractor extractor,
			ASTNode fromNode, ASTNode outcomeNode, Bag<Edit> outcomeEdits, TutorHint bestHint,
			HintOutcome outcome) {
//		if (!bestHint.validity.isAtLeast(Validity.MultipleTutors)) return;
		Bag<Edit> tutorEdits = new TreeBag<>();
		if (bestHint != null) {
			System.out.printf("Tutor Hint (%s):\n", bestHint.hintID);
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(
					fromNode, bestHint.to, config, PARTIAL_UNSEEN_VALUE);
			System.out.println(Diff.diff(
					fromNode.prettyPrint(true, config),
					tutorOutcomeNode.prettyPrint(true, config)));
			tutorEdits = extractor.getEdits(fromNode, tutorOutcomeNode);
		}
		System.out.printf("Alg Hint (%s):\n", outcome.id);
		System.out.println(Diff.diff(
				fromNode.prettyPrint(true, config),
				outcomeNode.prettyPrint(true, config), 2));
		EditExtractor.printEditsComparison(
				tutorEdits, outcomeEdits, "Tutor Hint", "Alg Hint");
		System.out.println("-------------------");
	}

	public static enum MatchType {
		None, Partial, Full;

		public boolean isAtLeast(MatchType type) {
			return this.ordinal() >= type.ordinal();
		}
	}

	@SuppressWarnings("serial")
	public static class HintRatingSet extends ArrayList<RequestRating> {
		public final String name;

		public HintRatingSet(String name) {
			this.name = name;
		}

		public void printSummary(String assignmentID) {
			List<RequestRating> ratings = stream()
					.filter(rating -> rating.assignmentID.equals(assignmentID))
					.collect(Collectors.toList());
			if (ratings.size() == 0) return;
			double qualityScoreFull = 0;
			double qualityScorePartial = 0;
			for (RequestRating rating : ratings) {
				qualityScoreFull += rating.qualityScore(MatchType.Full);
				qualityScorePartial += rating.qualityScore(MatchType.Partial);
			}
			int nRatings = ratings.size();
			qualityScoreFull /= nRatings;
			qualityScorePartial /= nRatings;

			double priorityMeanFull = stream()
					.mapToDouble(rating -> rating.priorityScore(false))
					.average().getAsDouble();
			double priorityMeanPartial = stream()
					.mapToDouble(rating -> rating.priorityScore(true))
					.average().getAsDouble();
			System.out.printf("TOTAL: %.03f (%.03f)v / %.03f (%.03f)p\n",
					qualityScoreFull, qualityScorePartial,
					priorityMeanFull, priorityMeanPartial);
		}

		public void writeAllHints(String path) throws FileNotFoundException, IOException {
			Spreadsheet spreadsheet = new Spreadsheet();
			writeAllHints(spreadsheet);
			spreadsheet.write(path);
		}

		public void writeAllHints(Spreadsheet spreadsheet) {
			forEach(rating -> rating.writeAllHints(spreadsheet));
		}

		public void writeAllRatings(Spreadsheet spreadsheet) {
			forEach(rating -> rating.writeRating(spreadsheet));
		}
	}

	@SuppressWarnings("serial")
	public static class RequestRating extends ArrayList<HintRating> {
		public final String requestID;
		public final String assignmentID;
		public final ASTNode requestNode;
		private final RatingConfig config;

		public RequestRating(String requestID, String assignmentID, ASTNode requestNode,
				RatingConfig config) {
			this.requestID = requestID;
			this.assignmentID = assignmentID;
			this.requestNode = requestNode;
			this.config = config;
		}

		public void sort() {
			sort((r1, r2) -> r1.hint == null ? 0 : r1.hint.compareTo(r2.hint));
		}

		public void writeAllHints(Spreadsheet spreadsheet) {
			for (int i = 0; i < size(); i++) {
				get(i).addToSpreadsheet(spreadsheet, i, getTotalWeight(), requestNode, config);
			}
			if (isEmpty()) {
				HintOutcome noOutcome = new HintOutcome(null, assignmentID, requestID, 1);
				new HintRating(noOutcome)
				.addToSpreadsheet(spreadsheet, 0, 1, requestNode, config);
			}
		}

		public void writeRating(Spreadsheet spreadsheet) {
			spreadsheet.newRow();
			spreadsheet.put("assignmentID", assignmentID);
			spreadsheet.put("requestID", requestID);
			double weight = getTotalWeight();
			for (MatchType type : MatchType.values()) {
				if (type == MatchType.None) continue;
				double validityValue = weight == 0 ? 0 :
					(validWeight(type, true) / weight);
				spreadsheet.put("Valid_" + type, validityValue);
				spreadsheet.put("Valid_" + type + "_validWeight", validWeight(type, true));
				spreadsheet.put("Valid_" + type  + "_validCount", validWeight(type, false));
			}
			spreadsheet.put("totalWeight", weight);
			spreadsheet.put("totalCount", size());
		}

		public double validWeight(MatchType minMatchType, boolean useWeights) {
			return stream()
					.mapToDouble(rating -> rating.matchType.isAtLeast(minMatchType)
							&& rating.isValid() && !rating.isTooSoon()
							? (useWeights ? rating.hint.weight() : 1) : 0)
					.sum();
		}

		public double qualityScore(MatchType minMatchType) {
			return validWeight(minMatchType, true) / getTotalWeight();
		}

		private double getTotalWeight() {
			return stream().mapToDouble(r -> r.hint.weight()).sum();
		}

		protected double priorityScore(boolean countPartial) {
			if (isEmpty()) return 0;
			return stream()
					.filter(r -> r.priority() != null &&
							(countPartial || r.matchType == MatchType.Full))
					.mapToDouble(r -> r.hint.weight() * r.priority().points())
					.sum() / getTotalWeight();
		}

		public void printSummary() {
			System.out.printf("%s: %.02f (%.02f)v / %.02f (%.02f)p\n",
					requestID,
					qualityScore(MatchType.Full), qualityScore(MatchType.Partial),
					priorityScore(false), priorityScore(true));
		}

		private void printRatings(ASTNode from, RatingConfig config, List<TutorHint> validHints) {
			if (isEmpty()) return;
			HintOutcome firstOutcome = get(0).hint;
			System.out.println("+====+ " + firstOutcome.assignmentID + " / " +
					firstOutcome.requestID + " +====+");
			Set<TutorHint> valid = validHints.stream()
					.filter(hint -> hint.priority == null || hint.priority != Priority.TooSoon)
					.collect(Collectors.toSet());
			System.out.println(from.prettyPrint(true, config));
			for (int tooSoonRound = 0; tooSoonRound < 2; tooSoonRound++) {
				for (MatchType type : MatchType.values()) {
					boolean tooSoon = tooSoonRound == 1;
					List<HintRating> matching = stream()
							.filter(rating -> tooSoon ? rating.isTooSoon() :
								(!rating.isTooSoon() && rating.matchType == type))
							.collect(Collectors.toList());
					if (type == MatchType.None) continue;
					if (!matching.isEmpty()) {
						String label = tooSoon ? "Too Soon" : type.toString();
						System.out.println("               === " + label + " ===");
						for (HintRating rating : matching) {
							System.out.println("Hint ID: " + rating.hint.id);
							System.out.println("Weight: " + rating.hint.weight());
							System.out.println(rating.hint.resultString(from, config));
							System.out.println("-------");
							valid.remove(rating.match);
						}
					}
					if (tooSoon) break;
				}
			}
//			if (!valid.isEmpty()) System.out.println("               === Missed ===");
//			for (TutorHint missed : valid) {
//				System.out.println(missed.hintID);
//				System.out.println(missed.toDiff(config));
//				System.out.println("-------");
//			}
		}
	}

	public static class HintRating {
		public final HintOutcome hint;
		public final Bag<Edit> edits = new TreeBag<>();
		public final TutorHint match;
		public final MatchType matchType;

		public boolean isValid() {
			return match != null;
		}

		public boolean isTooSoon() {
			return match != null && match.priority == Priority.TooSoon;
		}

		public Priority priority() {
			return match == null ? null : match.priority;
		}

		public HintRating(HintOutcome hint) {
			this(hint, null, MatchType.None);
		}

		public HintRating(HintOutcome hint, TutorHint match, MatchType matchType) {
			this.hint = hint;
			this.match = match;
			this.matchType = matchType;
		}

		public void addEdits(ASTNode requestNode, EditExtractor extractor, RatingConfig config) {
			if (hint.result == null) return;
			requestNode = normalizeNodeValues(requestNode, config);
			ASTNode outcomeNode = normalizeNodeValues(hint.result, config);
			edits.clear();
			edits.addAll(extractor.extractEditsUsingCodeAlign(requestNode, outcomeNode));
		}

		public void addToSpreadsheet(Spreadsheet spreadsheet, int order, double totalWeight,
				ASTNode requestNode, RatingConfig config) {
			spreadsheet.newRow();
			spreadsheet.put("assignmentID", hint.assignmentID);
			spreadsheet.put("requestID", hint.requestID);
			spreadsheet.put("hintID", hint.id);
			spreadsheet.put("order", order);
			spreadsheet.put("weight", hint.weight());
			spreadsheet.put("weightNorm", hint.weight() / totalWeight);
			Integer matchID = null, priority = null;
			if (match != null) {
				matchID = match.hintID;
				priority = match.priority == null ? null : match.priority.value;
			}
			spreadsheet.put("matchID", matchID);
			spreadsheet.put("valid", isValid());
			spreadsheet.put("priority", priority);
			spreadsheet.put("type", matchType.toString());
			spreadsheet.put("outcome", hint.result == null ? "" : hint.result.toJSON().toString());
			ColorStyle oldStyle = Diff.colorStyle;
			Diff.colorStyle = ColorStyle.HTML;
			spreadsheet.put("diff", hint.result == null ?
					"" : ASTNode.diff(requestNode, hint.result, config));
			Diff.colorStyle = oldStyle;

			spreadsheet.put("requestTreeSize", requestNode.treeSize());
			int nInsertions = 0, nDeletions = 0, nRelabels = 0;
			for (Edit edit : edits) {
				if (edit instanceof Insertion) nInsertions++;
				else if (edit instanceof Deletion) nDeletions++;
				else if (edit instanceof Relabel) nRelabels++;
			}
			spreadsheet.put("nInsertions", nInsertions);
			spreadsheet.put("nDeletions", nDeletions);
			spreadsheet.put("nRelabels", nRelabels);

			Map<String, String> properties = hint.getDebuggingProperties(requestNode);
			for (String key : properties.keySet()) {
				spreadsheet.put("p_" + key, properties.get(key));
			}
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(hint.assignmentID).append(" / ").append(hint.requestID)
			.append(": ").append(hint.weight())
			.append(" - ").append(matchType);
			return sb.toString();
		}
	}
}
