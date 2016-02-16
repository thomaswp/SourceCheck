package com.snap.eval.policy;

import java.util.Set;

import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public interface HintPolicy {
	Set<Node> nextSteps(Node node);
	Tuple<Node, Integer> solution(Node node, int maxSteps);
}
