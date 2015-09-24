package com.snap.data;

public abstract class Block extends Code {
	private static final long serialVersionUID = 1L;

	public final String name;

	@SuppressWarnings("unused")
	private Block() {
		this(null);
	}
	
	public Block(String type) {
		this.name = type;
	}
}
