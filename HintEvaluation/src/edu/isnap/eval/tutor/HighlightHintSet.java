package edu.isnap.eval.tutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.python.PythonHintConfig;
import edu.isnap.eval.tutor.TutorEdits.PrintableTutorHint;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintOutcome;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RatingConfig;

public abstract class HighlightHintSet extends HintSet {

	protected final HintConfig hintConfig;

	protected abstract HintHighlighter getHighlighter(HintRequest request, HintMap baseMap);

	public HighlightHintSet(String name, HintConfig hintConfig) {
		super(name, getRatingConfig(hintConfig));
		this.hintConfig = hintConfig;
	}

	public static RatingConfig getRatingConfig(HintConfig config) {
		if (config instanceof SnapHintConfig) return RatingConfig.Snap;
		if (config instanceof PythonHintConfig) return RatingConfig.Python;
		System.err.println("Unknown hint config: " + config.getClass().getName());
		return RatingConfig.Default;
	}

	public HighlightHintSet addHints(List<HintRequest> requests) {
		HintMap baseMap = new HintMap(hintConfig);

		for (HintRequest request : requests) {
			HintHighlighter highlighter = getHighlighter(request, baseMap);
			Node code = JsonAST.toNode(request.code, hintConfig.getNodeConstructor());
			code = copyWithIDs(code);
			System.out.println(code.prettyPrint());
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
				ASTNode outcomeNode = to.toASTNode();
				if (outcomeNode.hasType("snapshot")) outcomeNode.type = "Snap!shot";
				double priority = hint.priority.consensus();
//				if (priority < 0.35) continue;
				HintOutcome outcome = new HighlightOutcome(request.code, outcomeNode,
						request.assignmentID, request.id, priority);
				add(outcome);
			}
		}
		finish();
		return this;
	}

	public static Node copyWithIDs(Node node) {
		return copyWithIDs(node, null, new AtomicInteger(0));
	}

	private static Node copyWithIDs(Node node, Node parent, AtomicInteger count) {
		String id = node.id;
		if (id == null) id = "GEN_" + count.getAndIncrement();
		Node copy = node.constructNode(parent, node.type(), node.value, id);
		for (Node child : node.children) {
			copy.children.add(copyWithIDs(child, node, count));
		}
		return copy;
	}

	// Filter out hints that wouldn't be shown in iSnap anyway
	protected List<EditHint> filterHints(List<EditHint> hints, boolean filterSameParentInsertions) {
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

	public void addHints(GoldStandard standard) {
		addHints(standard.getHintRequests());
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

		public HighlightOutcome(ASTNode from, ASTNode result, String assignmentID, String requestID,
				double weight) {
			super(result, assignmentID, requestID, weight);
			this.from = from;
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
						outcome.assignmentID, outcome.from,
						outcome.result, Diff.diff(from, to)));
			}
		}
		Diff.colorStyle = ColorStyle.ANSI;
		return edits;
	}

}
