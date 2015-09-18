package com.snap.data;

public abstract class Block extends Code {
	public final String name;
	
	public Block(String type) {
		this.name = type;
	}
}
