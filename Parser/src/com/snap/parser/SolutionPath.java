package com.snap.parser;

import java.util.Iterator;
import java.util.LinkedList;

public class SolutionPath implements Iterable<DataRow> {
	
	public final LinkedList<DataRow> rows = new LinkedList<DataRow>();
	public boolean exported;
	
	public void add(DataRow row) {
		rows.add(row);
	}
	
	public int size() {
		return rows.size();
	}

	@Override
	public Iterator<DataRow> iterator() {
		return rows.iterator();
	}
}
