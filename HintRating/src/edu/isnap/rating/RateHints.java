package edu.isnap.rating;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.EditExtractor.Edit;
import edu.isnap.rating.EditExtractor.NodeReference;
import edu.isnap.rating.TutorHint.Priority;
import edu.isnap.rating.TutorHint.Validity;

public class RateHints {

	public final static String GS_SPREADSHEET = "gold-standard.csv";
	public final static String ALGORITHMS_DIR = "algorithms";
	public final static String TRAINING_DIR = "training";

	public final static String DATA_ROOT_DIR = "../data/hint-rating/";
	public final static String ISNAP_DATA_DIR = DATA_ROOT_DIR + "isnap2017/";
	public final static String ITAP_DATA_DIR = DATA_ROOT_DIR + "itap2016/";

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

	public static HintRatingSet rate(GoldStandard standard, HintSet hintSet) {
		return rate(standard, hintSet, false);
	}

	public static HintRatingSet rate(GoldStandard standard, HintSet hintSet, boolean debug) {
		HintRatingSet ratingSet = new HintRatingSet(hintSet.name);
		EditExtractor extractor = new EditExtractor(hintSet.config.areNodeIDsConsistent());
		for (String assignmentID : standard.getAssignmentIDs()) {
			System.out.println("----- " + assignmentID + " -----");

			for (String requestID : standard.getRequestIDs(assignmentID)) {
				RequestRating requestRating = new RequestRating(requestID, assignmentID);

				List<TutorHint> validHints = standard.getValidEdits(assignmentID, requestID);
				if (validHints.size() == 0) continue;
				List<HintOutcome> hints = hintSet.getOutcomes(requestID);

				// TODO: Stop if consensus hints is 0 (but only if consensus exists...)
				// TODO: Rating should be 0 if there are validHints but no generated hitns
				if (hints == null || hints.size() == 0) continue;

				for (HintOutcome hint : hints) {
					HintRating rating = findMatchingEdit(validHints, hint, hintSet.config,
							extractor);
					requestRating.add(rating);
				}
				if (debug) {
					requestRating.printRatings(validHints.get(0).from, hintSet.config, validHints);
				}
				ratingSet.add(requestRating);
				requestRating.printSummary();
			}

			ratingSet.printSummary(assignmentID);
		}
		return ratingSet;
	}

	private static ASTNode pruneImmediateChildren(ASTNode node, Predicate<String> condition) {
		for (int i = 0; i < node.children().size(); i++) {
			ASTNode child = node.children().get(i);
			if (condition.test(child.type) && child.children().isEmpty()) {
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
					//
					(node.children().size() == 0 && config.trimIfChildless(node.type()))) {
				node.parent().removeChild(node.index());
			}
		}

		// If node IDs are consistent, we can identify new nodes and prune their children.
		// Note that we could theoretically do this even if they aren't, using the EditExtractor's
		// TED algorithm, but currently only Snap needs this feature, and it has consistent IDs.
		if (config.areNodeIDsConsistent()) {
			// Get a list of node reference in the original AST, so we can see which have been added
			Set<NodeReference> fromRefs = new HashSet<>();
			from.recurse(node -> fromRefs.add(EditExtractor.getReferenceAsChild(node)));

			for (ASTNode node : toNodes) {
				if (node.parent() == null) continue;

				// If this node was created in the hint, prune its immediate children for nodes
				// that are added automatically, according to the config, e.g. literal nodes in Snap
				// We get a reference that includes at least the node's parent, so we can prune
				// moved nodes as well.
				NodeReference toRef = EditExtractor.getReferenceAsChild(node);
				if (!fromRefs.contains(toRef)) {
					pruneImmediateChildren(node, config::trimIfParentIsAdded);
				}
			}
		}
	}

	public static ASTNode normalizeNewValuesTo(ASTNode from, ASTNode to, RatingConfig config) {
		to = to.copy();

		// Get a set of all the node values used in the original AST. We don't differentiate values
		// by type, since multiple types can share values (e.g. varDecs and vars)
		Set<String> usedValues = new HashSet<>();
		from.recurse(node -> usedValues.add(node.value));

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
					// If so, we replace its value with a standard value, so all new values appear
					// the same
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
		}

		return to;
	}

	public static HintRating findMatchingEdit(List<TutorHint> validHints, HintOutcome outcome,
			RatingConfig config, EditExtractor extractor) {
		if (validHints.isEmpty()) return new HintRating(outcome);

		ASTNode fromNode = validHints.get(0).from;
		ASTNode outcomeNode = normalizeNewValuesTo(fromNode, outcome.result, config);
		pruneNewNodesTo(fromNode, outcomeNode, config);
		for (TutorHint tutorHint : validHints) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, tutorHint.to, config);
			pruneNewNodesTo(fromNode, tutorOutcomeNode, config);
			if (outcomeNode.equals(tutorOutcomeNode)) {
				return new HintRating(outcome, tutorHint, MatchType.Full);
			}

			if (outcome.result.equals(tutorHint.to)) {
				System.out.println("Matching hint:");
				System.out.println(ASTNode.diff(tutorHint.from, outcome.result, config));
				System.out.println("Difference in normalized nodes:");
				System.out.println(ASTNode.diff(tutorOutcomeNode, outcomeNode, config, 2));
				System.out.println("Tutor normalizing:");
				System.out.println(ASTNode.diff(tutorHint.to, tutorOutcomeNode, config, 2));
				System.out.println("Outcome normalizing:");
				System.out.println(ASTNode.diff(outcome.result, outcomeNode, config, 2));
				throw new RuntimeException("Normalized nodes should be equal if nodes are equal!");
			}
		}

		outcomeNode = normalizeNewValuesTo(fromNode, outcome.result, config);
		Set<Edit> outcomeEdits = extractor.getEdits(fromNode, outcomeNode);
		if (outcomeEdits.size() == 0) return new HintRating(outcome);
		Set<Edit> bestOverlap = new HashSet<>();
		TutorHint bestHint = null;
		// TODO: sort by validity and priority first
//		Collections.sort(validHints);
		for (TutorHint tutorHint : validHints) {
			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, tutorHint.to, config);
			// TODO: Figure out what to do if nodes don't have IDs (i.e. Python)
			// TODO: Also figure confirm if this is over-generous with Python
			Set<Edit> tutorEdits = extractor.getEdits(fromNode, tutorOutcomeNode);
			if (tutorEdits.size() == 0) continue;
			Set<Edit> overlap = new HashSet<>(tutorEdits);
			overlap.retainAll(outcomeEdits);
			if (overlap.size() == outcomeEdits.size()) {
				if (overlap.size() == tutorEdits.size()) {
					throw new RuntimeException("Edits should not match if hint outcomes did not!");
				}
				bestOverlap = overlap;
				bestHint = tutorHint;
				break;
			}
		}
		if (!bestOverlap.isEmpty()) {
//			ASTNode tutorOutcomeNode = normalizeNewValuesTo(fromNode, bestHint.to, config);
//			System.out.println("Tutor Hint:");
//			System.out.println(Diff.diff(
//					fromNode.prettyPrint(true, config),
//					tutorOutcomeNode.prettyPrint(true, config)));
//			System.out.println("Alg Hint:");
//			System.out.println(Diff.diff(
//					fromNode.prettyPrint(true, config),
//					outcomeNode.prettyPrint(true, config), 2));
//			Set<Edit> tutorEdits = extractor.getEdits(bestHint.from, tutorOutcomeNode);
//			EditExtractor.printEditsComparison(tutorEdits, outcomeEdits, "Tutor Hint", "Alg Hint");
//			System.out.println("-------------------");

			return new HintRating(outcome, bestHint, MatchType.Partial);
		}
		return new HintRating(outcome);
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
			double[] validityArrayMean = null;
			for (RequestRating rating : ratings) {
				double[] ratingArray = rating.getValidityArray();
				if (validityArrayMean == null) {
					validityArrayMean = ratingArray;
				} else {
					for (int i = 0; i < validityArrayMean.length; i++) {
						validityArrayMean[i] += ratingArray[i];
					}
				}
			}
			int nSnapshots = ratings.size();
			for (int i = 0; i < validityArrayMean.length; i++) validityArrayMean[i] /= nSnapshots;
			double priorityMean = stream()
					.mapToDouble(RequestRating::getPriorityScore)
					.average().getAsDouble();
			System.out.printf("TOTAL: %s / %.03fp\n",
					RequestRating.validityArrayToString(validityArrayMean, 3), priorityMean);
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

		public RequestRating(String requestID, String assignmentID) {
			this.requestID = requestID;
			this.assignmentID = assignmentID;
		}

		public void writeAllHints(Spreadsheet spreadsheet) {
			forEach(rating -> rating.addToSpreadsheet(spreadsheet));
		}

		public void writeRating(Spreadsheet spreadsheet) {
			spreadsheet.newRow();
			spreadsheet.put("assignmentID", assignmentID);
			spreadsheet.put("requestID", requestID);
			double weight = getTotalWeight();
			for (Validity validity : Validity.values()) {
				if (validity == Validity.NoTutors) continue;
				for (MatchType type : MatchType.values()) {
					if (type == MatchType.None) continue;
					spreadsheet.put(validity + "_" + type, validityWeight(type, validity) / weight);
				}
			}
		}

		public double validityWeight(MatchType minMatchType, Validity minValidity) {
			return stream()
					.mapToDouble(rating -> rating.matchType.isAtLeast(minMatchType)
							&& rating.validity().isAtLeast(minValidity) && !rating.isTooSoon()
							? rating.hint.weight() : 0)
					.sum();
		}

		private double getTotalWeight() {
			return stream().mapToDouble(r -> r.hint.weight()).sum();
		}

		protected double[] getValidityArray() {
			int nValues = Validity.values().length - 1;
			double[] validityArray = new double[nValues * 2];
			for (int i = 0; i < nValues; i++) {
				validityArray[i * 2] = validityWeight(MatchType.Full, Validity.fromInt(i + 1));
				validityArray[i * 2 + 1] =
						validityWeight(MatchType.Partial, Validity.fromInt(i + 1));
			}
			double totalWeight = getTotalWeight();;
			for (int i = 0; i < validityArray.length; i++) {
				validityArray[i] /= totalWeight;
			}
			return validityArray;
		}

		protected double getPriorityScore() {
			// TODO: figure out whether to count invalid items
			return stream()
					.filter(r -> r.priority() != null)
					.mapToDouble(r -> r.hint.weight() * r.priority().points())
					.sum() / getTotalWeight();
		}

		public void printSummary() {
			double[] validityArray = getValidityArray();
			System.out.printf("%s: %s / %.02fp\n",
					requestID, validityArrayToString(validityArray, 2), getPriorityScore());
		}

		private static String validityArrayToString(double[] array, int digits) {
			String format = "%.0" + digits + "f (%.0" + digits + "f)";
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			for (int i = 0; i < array.length / 2; i++) {
				if (i > 0) sb.append(", ");
				sb.append(String.format(format, array[i * 2], array[i * 2 + 1]));
			}
			sb.append("]v");
			return sb.toString();
		}

		private void printRatings(ASTNode from, RatingConfig config, List<TutorHint> validHints) {
			if (isEmpty()) return;
			HintOutcome firstOutcome = get(0).hint;
			System.out.println("+====+ " + firstOutcome.assignmentID + " / " +
					firstOutcome.requestID + " +====+");
			Set<TutorHint> valid = validHints.stream()
					.filter(hint -> hint.validity.isAtLeast(Validity.MultipleTutors) &&
							(hint.priority == null || hint.priority != Priority.TooSoon))
					.collect(Collectors.toSet());
			System.out.println(from.prettyPrint(true, config));
			for (int tooSoonRound = 0; tooSoonRound < 2; tooSoonRound++) {
				for (MatchType type : MatchType.values()) {
					boolean tooSoon = tooSoonRound == 1;
					List<HintRating> matching = stream()
							.filter(rating -> tooSoon ? rating.isTooSoon() :
								(rating.matchType == type && !rating.isTooSoon()))
							.collect(Collectors.toList());
					if (!matching.isEmpty()) {
						String label = tooSoon ? "Too Soon" : type.toString();
						System.out.println("               === " + label + " ===");
						for (HintRating rating : matching) {
							System.out.println("Weight: " + rating.hint.weight());
							System.out.println(rating.hint.resultString());
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
		public final TutorHint match;
		public final MatchType matchType;

		public Validity validity() {
			return match == null ? Validity.NoTutors : match.validity;
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

		public void addToSpreadsheet(Spreadsheet spreadsheet) {
			spreadsheet.newRow();
			spreadsheet.put("assignmentID", hint.assignmentID);
			spreadsheet.put("requestID", hint.requestID);
			spreadsheet.put("weight", hint.weight());
			Integer matchID = null, validity = null, priority = null;
			if (match != null) {
				matchID = match.hintID;
				validity = match.validity.value;
				priority = match.priority == null ? null : match.priority.value;
			}
			spreadsheet.put("matchID", matchID);
			spreadsheet.put("validity", validity);
			spreadsheet.put("priority", priority);
			spreadsheet.put("type", matchType.toString());
			Map<String, String> properties = hint.getDebuggingProperties();
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
