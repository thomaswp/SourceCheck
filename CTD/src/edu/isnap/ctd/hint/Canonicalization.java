package edu.isnap.ctd.hint;

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

	public static class OrderGroup extends Canonicalization {
		public final int group;

		public OrderGroup(int group) {
			this.group = group;
		}
	}
}
