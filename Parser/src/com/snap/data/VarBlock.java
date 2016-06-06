package com.snap.data;


public class VarBlock extends Block {
	private static final long serialVersionUID = 1L;
	
	@SuppressWarnings("unused")
	private VarBlock() {
		this(null, null);
	}
	
	public VarBlock(String name, String id) {
		super(name, id);
	}
	
	@Override
	public String toCode(boolean canon) {
		return canon ? "var" : name;
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		return toCode(canon);
	}
}
