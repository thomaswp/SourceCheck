package edu.isnap.eval.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.hint.CTDHintGenerator;
import edu.isnap.ctd.hint.VectorHint;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.Tuple;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Predicate;

public class HintFactoryPolicy implements HintPolicy {

	public final CTDHintGenerator generator;

	// Don't forget you're not evaluating these hints
	private final static Predicate ignoreHints = new Node.TypePredicate("stage", "sprite", "customBlock");


	public HintFactoryPolicy(HintData builder) {
		this.generator = builder.hintGenerator();
	}


	@Override
	public Set<Node> nextSteps(Node node) {
		HashSet<Node> steps = new HashSet<>();

		List<VectorHint> hints = generator.getHints(node);
		for (VectorHint hint : hints) {
			if (hint.caution) continue;
			if (ignoreHints.eval(hint.root)) continue;

			Node next = hint.outcome().root();
			steps.add(next);
		}

		return steps;
	}

	@Override
	public Tuple<Node, Integer> solution(Node node, int maxSteps) {
		int steps = 0;
		Node state = node;
		while (steps < maxSteps) {
			VectorHint hint = generator.getFirstHint(state);
			if (hint == null) break;
			state = hint.outcome().root();
			steps++;
		}
		return new Tuple<>(state, steps);
	}

}
