package com.snap.graph.data;

import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class VectorGraph extends OutGraph<VectorState> {

	public void generateEdges() {
		generateEdges(1, 0.15f);
	}
	
	public void generateEdges(int maxDis, double maxNDis) {
		int n = vertices.size();
		VectorState[] vertices = this.vertices.toArray(new VectorState[n]);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < i; j++) {
				VectorState a = vertices[i];
				VectorState b = vertices[j];
				Tuple<Integer,Double> distances = VectorState.distances(a, b);
				if (distances.x <= maxDis || distances.y <= maxNDis) {
					Edge<VectorState, Void> edge1 = addAndGetEdge(a, b, null);
					if (edge1 != null) edge1.synthetic = true;
					Edge<VectorState, Void> edge2 = addAndGetEdge(b, a, null);
					if (edge2 != null) edge2.synthetic = true;
				}
			}
		}
	}
	
}
