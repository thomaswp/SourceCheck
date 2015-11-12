package com.snap.graph.data;

import java.util.Arrays;
import java.util.Collection;

import com.snap.graph.Alignment;

public class IndexedVectorState extends VectorState {
	
	public final int index;
	private final String[] itemsBefore, itemsAfter;
	
	@SuppressWarnings("unused")
	private IndexedVectorState() {
		this(new String[0], -1);
	}
	
	public IndexedVectorState(Collection<String> items, int index) {
		this(items.toArray(new String[items.size()]), index);
	}
	
	public IndexedVectorState(String[] items, int index) {
		super(items);
		this.index = index;
		
		if (index < 0) {
			itemsBefore = itemsAfter = new String[0];
		} else {
			itemsBefore = Arrays.copyOfRange(items, 0, index);
			itemsAfter = Arrays.copyOfRange(items, index, items.length);
		}
		
		cache();
	}
	
	public static int distance(IndexedVectorState a, IndexedVectorState b) {
		return Alignment.alignCost(a.itemsBefore, b.itemsBefore, 1, 1) + 
				Alignment.alignCost(a.itemsAfter, b.itemsAfter, 1, 1);
	}
	
	@Override
	protected String getItem(int index) {
		return (index == this.index ? "*" : "") + super.getItem(index);
	}
	
}
