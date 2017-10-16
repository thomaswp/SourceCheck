package edu.isnap.parser.elements.util;

import java.util.List;

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

		public <T> Reorder(List<T> originalList, List<T> reordered) {
			reordering = new int[originalList.size()];

			for (int i = 0; i < reordering.length; i++) {
				T item = originalList.get(i);
				for (int j = 0; j < reordered.size(); j++) {
					if (reordered.get(j) == item) {
						reordering[i] = j;
						break;
					}
				}
			}
		}
	}
}
