package edu.isnap.ctd.hint.feature;

import java.util.List;

import edu.isnap.ctd.graph.InteractionGraph;
import edu.isnap.ctd.graph.Node;

public class FeatureGraph {

	public final List<Feature> features;
	public final InteractionGraph<FeatureState> graph = new InteractionGraph<>();

	private Node lastNode;
	private FeatureState lastState;

	public FeatureGraph(List<Feature> features) {
		this.features = features;
	}

	private FeatureState getState(Node node) {
		if (node == null) return FeatureState.empty(features.size());
		if (node != lastNode) {
			lastNode = node;
			lastState = new FeatureState(node, features);
		}
		return lastState;
	}

	public void addEdge(Node from, Node to) {
		graph.addEdge(getState(from), getState(to));
	}

	public void addGraph(FeatureGraph graph) {
		this.graph.addGraph(graph.graph, true);
	}

	public void addSolution(Node solution) {
		graph.setGoal(getState(solution), true);
	}

}
