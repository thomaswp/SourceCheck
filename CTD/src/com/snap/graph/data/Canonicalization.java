package com.snap.graph.data;

public abstract class Canonicalization {
	
	public static class SwapArgs extends Canonicalization {
		
	}
	
	public static class Rename extends Canonicalization {
		public final String name;
		
		public Rename(String name) {
			this.name = name;
		}
	}
	
	public static class InvertOp extends Rename {
		public InvertOp(String name) {
			super(name);
		}
		
	}
}
