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
					if (!hasEdge(a, b)) addOrGetEdge(a, b, null).synthetic = true;
					if (!hasEdge(b, a)) addOrGetEdge(b, a, null).synthetic = true;
				}
			}
		}
	}
	
}
