package com.github.javaparser.javaparser_symbol_solver_core;

import java.util.ArrayList;
import java.util.Arrays;

public class ParsedNode {
	private String type;
	private String value;
	private ArrayList<ParsedNode> children;
	private int[] sourceStart;
	private int[] sourceEnd;
	
	public ParsedNode(String type, String value, ArrayList<ParsedNode> children, int[] sourceStart, int[] sourceEnd) {
		super();
		this.type = type;
		this.value = value;
		this.children = children;
		this.sourceStart = sourceStart;
		this.sourceEnd = sourceEnd;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public ArrayList<ParsedNode> getChildren() {
		return children;
	}
	public void setChildren(ArrayList<ParsedNode> children) {
		this.children = children;
	}
	public int[] getSourceStart() {
		return sourceStart;
	}
	public void setSourceStart(int[] sourceStart) {
		this.sourceStart = sourceStart;
	}
	public int[] getSourceEnd() {
		return sourceEnd;
	}
	public void setSourceEnd(int[] sourceEnd) {
		this.sourceEnd = sourceEnd;
	}
	@Override
	public String toString() {
		return "ParsedNode [type=" + type + ", value=" + value + ", children=" + children + ", sourceStart="
				+ Arrays.toString(sourceStart) + ", sourceEnd=" + Arrays.toString(sourceEnd) + "]";
	}
}
