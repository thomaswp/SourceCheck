package edu.isnap.hint;

public abstract class Canonicalization {

	public static class SwapBinaryArgs extends Canonicalization {

	}

	public static class Rename extends Canonicalization {
		public final String name;

		public Rename(String name) {
			this.name = name;
		}
	}

	public static class Reorder extends Canonicalization {
		public final int[] reordering;

		public Reorder(int[] reordering) {
			this.reordering = reordering;
		}
	}
}
