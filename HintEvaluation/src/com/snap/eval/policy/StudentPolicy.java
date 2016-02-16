package com.snap.eval.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class StudentPolicy implements HintPolicy {
	
	public final List<Node> nodes;
	
	public StudentPolicy(List<Node> nodes) {
		this.nodes = nodes;
	}
	
	@Override
	public Set<Node> nextSteps(Node node) {
		Set<Node> set = new HashSet<>();

		for (int i = 0; i < nodes.size() - 1; i++) {
			if (nodes.get(i) == node) {
				set.add(nodes.get(i + 1));
				break;
			}
		}
		
		return set;
	}

	@Override
	public Tuple<Node, Integer> solution(Node node, int maxSteps) {
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i) == node) {
				return new Tuple<Node, Integer>(nodes.get(nodes.size() - 1), nodes.size() - i);
			}
		}
		return new Tuple<Node, Integer>(nodes.get(nodes.size() - 1), -1);
	}
}
