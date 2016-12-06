package edu.isnap.eval.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.ctd.hint.VectorHint;
import edu.isnap.ctd.util.Tuple;

public class HintFactoryPolicy implements HintPolicy {

	public final HintMapBuilder builder;
	public final int chain;

	// Don't forget you're not evaluating these hints
	private final static Predicate ignoreHints = new Node.TypePredicate("stage", "sprite", "customBlock");

	public HintFactoryPolicy(HintMapBuilder builder) {
		this(builder, 1);
	}

	public HintFactoryPolicy(HintMapBuilder builder, int chain) {
		this.builder = builder;
		this.chain = chain;
	}


	@Override
	public Set<Node> nextSteps(Node node) {
		HashSet<Node> steps = new HashSet<>();

		List<VectorHint> hints = builder.getHints(node, chain);
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
			VectorHint hint = builder.getFirstHint(state);
			if (hint == null) break;
			state = hint.outcome().root();
			steps++;
		}
		return new Tuple<>(state, steps);
	}

}
