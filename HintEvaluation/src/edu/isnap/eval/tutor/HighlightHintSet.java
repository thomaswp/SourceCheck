package edu.isnap.eval.tutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import edu.isnap.ctd.util.Tuple;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.tutor.TutorEdits.PrintableTutorHint;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintOutcome;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.Trace;

public abstract class HighlightHintSet extends HintMapHintSet {

	protected abstract HintHighlighter getHighlighter(HintRequest request, HintMap baseMap);

	public HighlightHintSet(String name, HintConfig hintConfig) {
		super(name, hintConfig);
	}

	/** Legacy method to support loading single-snapshot hint requests from a GoldStandard */
	public HighlightHintSet addHints(GoldStandard standard) {
		List<HintRequest> requests = new ArrayList<>();
		for (String assignmentID : standard.getAssignmentIDs()) {
			for (String requestID : standard.getRequestIDs(assignmentID)) {
				Trace trace = new Trace(requestID, assignmentID);
				trace.add(standard.getHintRequestNode(assignmentID, requestID).toSnapshot());
				requests.add(new HintRequest(trace));
			}
		}
		return addHints(requests);
	}

	@Override
	public HighlightHintSet addHints(List<HintRequest> requests) {
		HintMap baseMap = new HintMap(hintConfig);

		for (HintRequest request : requests) {
			HintHighlighter highlighter = getHighlighter(request, baseMap);
			Node code = JsonAST.toNode(request.code, hintConfig.getNodeConstructor());
			// Applying edits requires nodes to have meaningful IDs, so if they don't by default, we
			// generate them. We don't otherwise, since the generated IDs won't be consistent.
			code = config.areNodeIDsConsistent() ? code.copy() : copyWithIDs(code);
			List<EditHint> allHints = hintConfig.usePriority ?
					highlighter.highlightWithPriorities(code) :
						highlighter.highlight(code);
//			Set<EditHint> originalHints = new HashSet<>(allHints);
			Set<EditHint> hints = filterHints(allHints, false);

//			originalHints.removeAll(hints);
//			System.out.println(request.id);
//			System.out.println(request.code.prettyPrint(true, config));
//			System.out.println("Kept Hints:");
//			hints.forEach(System.out::println);
//			System.out.println("Removed Hints: ");
//			originalHints.forEach(System.out::println);
//			System.out.println("----------------\n");


			for (EditHint hint : hints) {
				List<EditHint> edits = Collections.singletonList(hint);
				Node to = code.copy();
				EditHint.applyEdits(to, edits);
				ASTNode outcomeNode = to.toASTNode();
				if (outcomeNode.hasType("snapshot")) outcomeNode.type = "Snap!shot";
				double weight;
				if (hintConfig.usePriority) {
					weight = hint.priority.consensus() * getDefaultWeight(hint);
//					if (priority < 0.25) continue;
				} else {
					weight = 1;
				}
				HighlightOutcome outcome = new HighlightOutcome(request.code, outcomeNode,
						request.assignmentID, request.id, weight, hint);
				add(outcome);
			}
		}
		finish();
		return this;
	}

	private static double getDefaultWeight(EditHint hint) {
//		if (hint instanceof Deletion) return 0.25f;
		return 1;
	}

	// Filter out hints that wouldn't be shown in iSnap anyway
	protected Set<EditHint> filterHints(List<EditHint> hints, boolean filterSameParentInsertions) {
		// Don't include insertions with a missing parent (yellow highlights), since they can't
		// be fully carried out yet
		List<Insertion> insertions = hints.stream()
				.filter(h -> h instanceof Insertion)
				.map(h -> (Insertion)h)
				.filter(i -> !i.missingParent)
				.collect(Collectors.toList());

		// If instructed, filter out insertions at the same index of the same parent, since only the
		// first of these will actually be shown
		if (filterSameParentInsertions) {
			ListMap<Tuple<Node, Integer>, Insertion> parentMap = new ListMap<>();
			insertions.stream().filter(i -> i.parent.hasType("script"))
			.forEach(i -> parentMap.add(new Tuple<>(i.parent, i.index), i));

			for (List<Insertion> childEdits : parentMap.values()) {
				Insertion best = childEdits.stream().max((a, b) -> {
					// TODO: Find a better way than order to prioritize insertions at the same place
//					int cp = -Double.compare(a.priority.consensus(), b.priority.consensus());
//					if (cp != 0) return cp;
					// Break ties with the original ordering
					return -Integer.compare(insertions.indexOf(a), insertions.indexOf(b));
				}).get();
				childEdits.stream().filter(i -> i != best)
				.forEach(i -> removeExactly(insertions, i));
			}
		}

		Set<EditHint> hintSet = hints.stream()
				.filter(h -> !(h instanceof Insertion))
				// Maybe? Last study showed these are not generally created by people
				.filter(h -> !(h instanceof Reorder))
				// Maybe? Technically we do show this, but it's just highlighting, and probably
				// should go away
				.filter(h -> !(h instanceof Deletion && ((Deletion) h).node.hasType("script")))
				.collect(Collectors.toSet());
		hintSet.addAll(insertions);
		return hintSet;
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

	private class HighlightOutcome extends HintOutcome {

		final ASTNode from;
		final EditHint editHint;

		public HighlightOutcome(ASTNode from, ASTNode result, String assignmentID, String requestID,
				double weight, EditHint editHint) {
			super(result, assignmentID, requestID, weight);
			this.from = from;
			this.editHint = editHint;
		}

		@Override
		public String resultString(ASTNode from, RatingConfig config) {
			return (editHint.priority == null ? "" : (editHint.priority.toString() + "\n")) +
					editHint.toString() + ":\n" +
					super.resultString(from, config);
		}

		@Override
		public Map<String, String> getDebuggingProperties() {
			Map<String, String> map = new LinkedHashMap<>();
			map.put("action", editHint.action());
			if (editHint.priority == null) return map;
			Map<String, Object> props = editHint.priority.getPropertiesMap();
			for (String key : props.keySet()) {
				Object value = props.get(key);
				map.put(key, value == null ? null : value.toString());
			}
			return  map;
		}
	}

	public List<PrintableTutorHint> toTutorEdits() {
		Diff.colorStyle = ColorStyle.HTML;
		List<PrintableTutorHint> edits = new ArrayList<>();
		int hintID = 0;
		for (String requestID : getHintRequestIDs()) {
			for (HintOutcome o : getOutcomes(requestID)) {
				HighlightOutcome outcome = (HighlightOutcome) o;
				String from = outcome.from.prettyPrint(true, config);
				String to = outcome.result.prettyPrint(true, config);
				edits.add(new PrintableTutorHint(hintID++, requestID, null,
						outcome.assignmentID, null, outcome.from,
						outcome.result, Diff.diff(from, to)));
			}
		}
		Diff.colorStyle = ColorStyle.ANSI;
		return edits;
	}

}
