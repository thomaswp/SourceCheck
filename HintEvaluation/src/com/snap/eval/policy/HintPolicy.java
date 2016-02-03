package com.snap.eval.policy;

import java.util.Set;

import com.snap.graph.data.Node;

public interface HintPolicy {
	Set<Node> nextSteps(Node node);
}
