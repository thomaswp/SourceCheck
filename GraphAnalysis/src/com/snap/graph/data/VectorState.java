package com.snap.graph.data;

import java.util.Collection;

import com.snap.graph.Alignment;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class VectorState extends StringHashable {

	public final String[] items;
	
	@SuppressWarnings("unused")
	private VectorState() {	
		items = null;
	}
	
	@Override
	protected boolean autoCache() {
		return true;
	}
	
	public VectorState(String[] items) {
		this.items = items;
		cache();
	}
	
	public VectorState(Collection<String> items) {
		this(items.toArray(new String[items.size()]));
	}
	
	@Override
	protected String toCanonicalStringInternal() {
		String out = "[";
		for (int i = 0; i < items.length; i++) {
			if (i > 0) out += ", ";
			out += getItem(i);
		}
		out += "]";
		return out;
	}
	
	public static double normalizedDirectionalDistance(VectorState a, VectorState b) {
		return distances(a, b).y;
	}
	
	public static int distance(VectorState a, VectorState b) {
		return distance(a, b, 1, 1, 1);
	}
	
	public static int distance(VectorState a, VectorState b, int insCost, int delCost, int subCost) {
		return Alignment.alignCost(a.items, b.items, insCost, delCost, subCost);
	}
	
	public static Tuple<Integer, Double> distances(VectorState a, VectorState b) {
		int distance = distance(a, b);
		int length = Math.max(a.items.length, b.items.length);
		double ndd = length == 0 ? 0 : (double)distance / length;
		return new Tuple<Integer, Double>(distance, ndd);
	}
	
	public String toJson() {
		return toJson(false);
	}
	
	public String toJson(boolean reverseArgs) {
		String out = "[";
		if (reverseArgs) {
			for (int i = items.length - 1; i >= 0; i--) {
				out = addItem(out, i);
			}
		} else {
			for (int i = 0; i < items.length; i++) {
				out = addItem(out, i);
			}
		}
		out += "]";
		return out;
	}

	private String addItem(String out, int i) {
		if (out.length() > 1) out += ", ";
		out += "\"" + getItem(i) + "\"";
		return out;
	}
	
	protected String getItem(int index) {
		return items[index];
	}
}
