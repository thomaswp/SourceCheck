package edu.isnap.eval.policy;

import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Tuple;

public interface HintPolicy {
	Set<Node> nextSteps(Node node);
	Tuple<Node, Integer> solution(Node node, int maxSteps);
}
