package com.snap.data;


public class VarBlock extends Block {
	private static final long serialVersionUID = 1L;
	
	@SuppressWarnings("unused")
	private VarBlock() {
		this(null);
	}
	
	public VarBlock(String name) {
		super(name);
	}
	
	@Override
	public String toCode() {
		return name;
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		return canon ? "var" : name;
	}
}
