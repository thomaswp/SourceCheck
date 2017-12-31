package edu.isnap.ctd.graph.vector;

import java.util.Arrays;
import java.util.Collection;

import edu.isnap.ctd.util.Alignment;

public class IndexedVectorState extends VectorState {

	public final int index;
	private final String[] itemsBefore, itemsAfter;

	@SuppressWarnings("unused")
	private IndexedVectorState() {
		this(new String[0], -1, 0);
	}

	public IndexedVectorState(Collection<String> items, int index) {
		this(items, index, Integer.MAX_VALUE);
	}

	public IndexedVectorState(Collection<String> items, int index, int maxSize) {
		this(items.toArray(new String[items.size()]), index, maxSize);
	}

	public IndexedVectorState(String[] items, int index, int maxSize) {
		super(items);
		this.index = index;

		if (index >= items.length) throw new IllegalArgumentException(index + " > " + items.length);
		if (index < 0 || items.length == 0) {
			itemsBefore = itemsAfter = new String[0];
		} else {
			int before = Math.min(index, maxSize);
			int after = Math.min(items.length - index - 1, maxSize);
			itemsBefore = Arrays.copyOfRange(items, index - before, index);
			itemsAfter = Arrays.copyOfRange(items, index + 1, index + 1 + after);
		}

		cache();
	}

	public static double distance(IndexedVectorState a, IndexedVectorState b) {
		return (Alignment.normAlignCost(a.itemsBefore, b.itemsBefore, 1, 1, 1) +
				Alignment.normAlignCost(a.itemsAfter, b.itemsAfter, 1, 1, 1)) / 2;
	}

	@Override
	protected String getItem(int index) {
		return (index == this.index ? "*" : "") + super.getItem(index);
	}

}
