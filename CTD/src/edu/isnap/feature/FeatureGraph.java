package edu.isnap.feature;

import java.util.List;

import edu.isnap.ctd.graph.InteractionGraph;
import edu.isnap.node.Node;

public class FeatureGraph {

	public final List<Feature> features;
	public final InteractionGraph<FeatureState> graph = new InteractionGraph<>();

	private Node lastNode;
	private FeatureState lastState;

	private final boolean[] current;
	private FeatureState currentState;

	public FeatureGraph(List<Feature> features) {
		this.features = features;
		current = new boolean[features.size()];
		currentState = FeatureState.empty(features.size());
	}

	private FeatureState getState(Node node) {
		if (node == null) return FeatureState.empty(features.size());
		if (node != lastNode) {
			lastNode = node;
			lastState = new FeatureState(node, features);
		}
		return lastState;
	}

	public void addNode(Node to) {
		FeatureState toState = getState(to);
		int changed = 0;
		for (int i = 0; i < current.length; i++) {
			if (!current[i] && toState.featuresPresent[i]) {
				current[i] = true;
				changed++;
			}
		}
		if (changed == 0) return;

		FeatureState nextState = new FeatureState(current);
		// Ignore jumps of more than one feature flip at a time (since these should reasonably be
		// independent based on how they are generated, we don't expect any to be naturally
		// co-occurring).
		if (changed == 1) {
			graph.addEdge(currentState, nextState);
		}
		currentState = nextState;
	}

	public void addGraph(FeatureGraph graph) {
		this.graph.addGraph(graph.graph, true);
	}

	public void addSolution(Node solution) {
		graph.setGoal(getState(solution), true);
	}

}
