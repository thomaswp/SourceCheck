package com.snap.parser;

import java.util.Iterator;
import java.util.LinkedList;

public class SolutionPath implements Iterable<DataRow>, IVersioned {
	
	public final static String VERSION = "1.2.0"; 
	
	public final LinkedList<DataRow> rows = new LinkedList<DataRow>();
	public final Grade grade;
	public boolean exported;
	
	private String version = VERSION; 
	
	@SuppressWarnings("unused")
	private SolutionPath() {
		this.grade = null;
	}
	
	public SolutionPath(Grade grade) {
		this.grade = grade;
	}
	
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

	@Override
	public boolean isUpToDate() {
		return version.equals(VERSION);
	}
}
