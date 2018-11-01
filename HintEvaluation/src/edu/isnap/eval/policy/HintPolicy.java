package edu.isnap.eval.policy;

import java.util.Set;

import edu.isnap.hint.util.Tuple;
import edu.isnap.node.Node;

public interface HintPolicy {
	Set<Node> nextSteps(Node node);
	Tuple<Node, Integer> solution(Node node, int maxSteps);
}
