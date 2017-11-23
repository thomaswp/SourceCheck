package edu.isnap.eval.agreement;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.edit.Deletion;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.hint.edit.Insertion;
import edu.isnap.ctd.hint.edit.Reorder;
import edu.isnap.ctd.util.NullSream;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.eval.agreement.RateHints.HintOutcome;
import edu.isnap.eval.agreement.RateHints.HintRequest;
import edu.isnap.eval.agreement.RateHints.HintSet;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.parser.Store.Mode;

public class HighlightHintSet extends HintSet {

	private final Dataset dataset;
	private final HintConfig config;

	public HighlightHintSet(String name, Dataset dataset, List<HintRequest> requests,
			HintConfig config) {
		super(name);
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
				highlighter.trace = NullSream.instance;
				highlighters.put(request.assignmentID, highlighter);
			}

			Node code = request.code.copy();
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
				HintOutcome outcome = new HintOutcome(to, request.id, priority, edits);
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

}
