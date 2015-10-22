package com.snap.graph.data;

import java.util.Arrays;
import java.util.Collection;

import com.snap.graph.Alignment;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class VectorState extends StringHashable {

	public final String[] items;
	
	@SuppressWarnings("unused")
	private VectorState() {	
		items = null;
	}
	
	public VectorState(Collection<String> items) {
		this.items = items.toArray(new String[items.size()]);
		cache();
	}
	
	@Override
	protected String toCanonicalStringInternal() {
		return Arrays.toString(items);
	}
	
	public static double normalizedDirectionalDistance(VectorState a, VectorState b) {
		return distances(a, b).y;
	}
	
	public static int distance(VectorState a, VectorState b) {
		return Alignment.alignCost(a.items, b.items, 1, 2);
	}
	
	public static Tuple<Integer, Double> distances(VectorState a, VectorState b) {
		int distance = distance(a, b);
		int length = Math.max(a.items.length, b.items.length);
		double ndd = length == 0 ? 0 : (double)distance / length;
		return new Tuple<Integer, Double>(distance, ndd);
	}
}
