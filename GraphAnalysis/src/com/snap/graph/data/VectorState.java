package com.snap.graph.data;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.snap.graph.Alignment;

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

	public int length() {
		return items.length;
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

	public static int distance(VectorState a, VectorState b, int insCost, int delCost,
			int subCost) {
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

	public int countOf(String item) {
		if (item == null) return 0;
		int count = 0;
		for (String i : items) {
			if (item.equals(i)) count++;
		}
		return count;
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

	public static VectorState empty() {
		return new VectorState(new String[0]);
	}

	public int overlap(VectorState other) {
		List<String> otherList = new LinkedList<>();
		for (String i : other.items) otherList.add(i);

		int count = 0;
		for (String i : items) {
			if (otherList.remove(i)) count++;
		}

		return count;
	}

	public VectorState limitTo(VectorState... states) {
		List<String> list = new LinkedList<>();
		for (String i : items) list.add(i);
		List<String> allowed = new LinkedList<>();
		for (VectorState state : states) {
			for (String i : state.items) allowed.add(i);
		}
		for (int i = 0; i < list.size(); i++) {
			if (!allowed.remove(list.get(i))) {
				list.remove(i--);
			}
		}
		return new VectorState(list);
	}
}
