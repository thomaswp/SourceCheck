package com.snap.eval.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.snap.graph.data.Hint;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.graph.data.Node;
import com.snap.graph.data.Node.Predicate;
import com.snap.graph.data.Tuple;
import com.snap.graph.subtree.HintGenerator;

public class HintFactoryPolicy implements HintPolicy {

	public final HintGenerator builder;
	public final int chain;

	// TODO: Don't forget you're not evaluating these hints
	private final static Predicate ignoreHints = new Node.TypePredicate("stage", "sprite", "customBlock");

	public HintFactoryPolicy(HintGenerator builder) {
		this(builder, 1);
	}

	public HintFactoryPolicy(HintGenerator builder, int chain) {
		this.builder = builder;
		this.chain = chain;
	}


	@Override
	public Set<Node> nextSteps(Node node) {
		HashSet<Node> steps = new HashSet<>();

		List<Hint> hints = builder.getHints(node, chain);
		for (Hint hint : hints) {
			VectorHint vHint = (VectorHint) hint;
			if (vHint.caution) continue;
			if (ignoreHints.eval(vHint.root)) continue;

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
			Hint hint = builder.getFirstHint(state);
			if (hint == null) break;
			state = hint.outcome().root();
			steps++;
		}
		return new Tuple<Node, Integer>(state, steps);
	}

}
