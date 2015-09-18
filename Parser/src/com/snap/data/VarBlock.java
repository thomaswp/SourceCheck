package com.snap.data;


public class VarBlock extends Block {
	
	public VarBlock(String name) {
		super(name);
	}
	
	@Override
	public String toCode() {
		return name;
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		return name;
	}
}
