package edu.isnap.eval.agreement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.edit.Deletion;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.hint.edit.Insertion;
import edu.isnap.ctd.hint.edit.Reorder;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.Diff.ColorStyle;
import edu.isnap.ctd.util.NullStream;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.eval.agreement.TutorEdits.PrintableTutorEdit;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.parser.Store.Mode;
import edu.isnap.rating.RateHints.HintOutcome;
import edu.isnap.rating.RateHints.HintRequest;
import edu.isnap.rating.RateHints.HintSet;
import edu.isnap.rating.RateHints.RatingConfig;

public class HighlightHintSet extends HintSet {

	private final Dataset dataset;
	private final HintConfig config;

	public final static RatingConfig SnapRatingConfig = new RatingConfig() {
		@Override
		public boolean useSpecificNumericLiterals() {
			return false;
		}

		@Override
		public boolean trimIfChildless(String type) {
			return "script".equals(type);
		}

		@Override
		public boolean trimIfParentIsAdded(String type) {
			return Agreement.prunable.contains(type);
		}

		@Override
		public boolean nodeTypeHasBody(String type) {
			return SnapNode.typeHasBody(type);
		}

	};

	public HighlightHintSet(String name, HintConfig config, Dataset dataset,
			List<HintRequest> requests) {
		super(name, SnapRatingConfig);
		this.dataset = dataset;
		this.config = config;
		addHints(requests);
	}

	private void addHints(List<HintRequest> requests) {
		Map<String, HintHighlighter> highlighters = new HashMap<>();

		Map<String, Assignment> assignmentMap = dataset.getAssignmentMap();
		HintMap baseMap = new HintMap(config);

		for (HintRequest request : requests) {
			HintHighlighter highlighter = highlighters.get(request.assignmentID);
			Assignment assignment = assignmentMap.get(request.assignmentID);
			if (highlighter == null) {
				SnapHintBuilder builder = new SnapHintBuilder(assignment, baseMap);
				highlighter = builder.buildGenerator(Mode.Ignore, 1).hintHighlighter();
				highlighter.trace = NullStream.instance;
				highlighters.put(request.assignmentID, highlighter);
			}

			Node code = JsonAST.toNode(request.code, SnapNode::new);
			List<EditHint> hints = highlighter.highlightWithPriorities(code);
			Set<EditHint> originalHints = new HashSet<>(hints);
			hints = filterHints(hints, false);
			originalHints.removeAll(hints);

//			System.out.println(request.id);
//			System.out.println(request.code.prettyPrint(true));
//			System.out.println("Kept Hints:");
//			hints.forEach(System.out::println);
//			System.out.println("Removed Hints: ");
//			originalHints.forEach(System.out::println);
//			System.out.println("----------------\n");


			for (EditHint hint : hints) {
				List<EditHint> edits = Collections.singletonList(hint);
				Node to = code.copy();
				EditHint.applyEdits(to, edits);
				double priority = hint.priority.consensus();
//				if (priority < 0.35) continue;
				HintOutcome outcome = new HighlightOutcome(request.code, request.assignmentID,
						to.toASTNode(), request.id, priority);
				add(request.id, outcome);
			}
		}
	}

	// Filter out hints that wouldn't be shown in iSnap anyway
	private List<EditHint> filterHints(List<EditHint> hints, boolean filterSameParentInsertions) {
		List<Insertion> insertions = hints.stream()
				.filter(h -> h instanceof Insertion)
				.map(h -> (Insertion)h)
				.filter(i -> !i.missingParent)
				.collect(Collectors.toList());

		if (filterSameParentInsertions) {
			ListMap<Node, Insertion> parentMap = new ListMap<>();
			insertions.stream().filter(i -> i.parent.hasType("script"))
			.forEach(i -> parentMap.add(i.parent, i));

			for (List<Insertion> childEdits : parentMap.values()) {
				Insertion best = childEdits.stream().max((a, b) -> {
					// Use consensus to find the most important insertion
					int cp = -Double.compare(a.priority.consensus(), b.priority.consensus());
					if (cp != 0) return cp;
					// Break ties with the original ordering
					return Integer.compare(insertions.indexOf(a), insertions.indexOf(b));
				}).get();
				childEdits.stream().filter(i -> i != best)
				.forEach(i -> removeExactly(insertions, i));
			}
		}

		hints = hints.stream()
				.filter(h -> !(h instanceof Insertion))
				// Maybe? Last study showed these are not generally created by people
				.filter(h -> !(h instanceof Reorder))
				// Maybe? Technically we do show this, but it's just highlighting, and probably
				// should go away
				.filter(h -> !(h instanceof Deletion && ((Deletion) h).node.hasType("script")))
				.collect(Collectors.toList());
		hints.addAll(insertions);
		return hints;
	}

	private static <T> boolean removeExactly(List<T> list, T element) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == element) {
				list.remove(i);
				return true;
			}
		}
		return false;
	}

	private static class HighlightOutcome extends HintOutcome {

		final ASTNode from;
		final String assignmentID;

		public HighlightOutcome(ASTNode from, String assignment, ASTNode outcome, int snapshotID,
				double weight) {
			super(outcome, snapshotID, weight);
			this.from = from;
			this.assignmentID = assignment;
		}

	}

	public List<PrintableTutorEdit> toTutorEdits() {
		Diff.colorStyle = ColorStyle.HTML;
		List<PrintableTutorEdit> edits = new ArrayList<>();
		int hintID = 0;
		for (int rowID : keySet()) {
			for (HintOutcome o : get(rowID)) {
				HighlightOutcome outcome = (HighlightOutcome) o;
				String from = outcome.from.prettyPrint(true, SnapRatingConfig::nodeTypeHasBody);
				String to = outcome.outcome.prettyPrint(true, SnapRatingConfig::nodeTypeHasBody);
				edits.add(new PrintableTutorEdit(hintID++, String.valueOf(rowID), null,
						outcome.assignmentID, outcome.from,
						outcome.outcome, Diff.diff(from, to)));
			}
		}
		Diff.colorStyle = ColorStyle.ANSI;
		return edits;
	}

}
