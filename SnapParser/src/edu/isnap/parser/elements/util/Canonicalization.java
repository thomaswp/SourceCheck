package edu.isnap.parser.elements.util;

public abstract class Canonicalization {

	public static class SwapSymmetricArgs extends Canonicalization {

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
