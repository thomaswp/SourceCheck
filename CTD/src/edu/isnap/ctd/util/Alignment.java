package edu.isnap.ctd.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Alignment {
	public static double normalizedAlignScore(String[] sequenceA, String[] sequenceB) {
		int cost = alignCost(sequenceA, sequenceB);
		int maxLength = Math.max(sequenceA.length, sequenceB.length);
		int aligned = maxLength - cost;
		return (double) (aligned + 1) / (maxLength + 1);
	}

	public static int alignCost(String[] sequenceA, String[] sequenceB) {
		return alignCost(sequenceA, sequenceB, 1, 1, 1);
	}

	public static double normAlignCost(String[] sequenceA, String[] sequenceB, int insCost,
			int delCost, int subCost) {
		int cost = alignCost(sequenceA, sequenceB, insCost, delCost, subCost);
		int length = Math.max(sequenceA.length, sequenceB.length);
		return length == 0 ? 0 : ((double) cost / length);
	}

	// Credit: http://introcs.cs.princeton.edu/java/96optimization/Diff.java.html
	public static int alignCost(String[] sequenceA, String[] sequenceB, int insCost, int delCost,
			int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, insCost, delCost, subCost, false);
		return opt[sequenceA.length][sequenceB.length];
	}

	public static List<int[]> alignPairs(String[] sequenceA, String[] sequenceB, int insCost,
			int delCost, int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, insCost, delCost, subCost, true);
		ArrayList<int[]> pairs = new ArrayList<>();

//		for (int[] row : opt) {
//			System.out.println(Arrays.toString(row));
//		}
		int i = 0, j = 0;
		while (i < sequenceA.length && j < sequenceB.length) {
			int replaceCost = opt[i+1][j+1];
			int skipACost = opt[i+1][j];
			int skipBCost = opt[i][j+1];

			if (sequenceA[i].equals(sequenceB[j]) && replaceCost >= 0) {
				pairs.add(new int[] {i, j});
				i++; j++;
				continue;
			}

			if (skipACost >= 0) {
				pairs.add(new int[] {i, -1});
				i++;
				continue;
			}

			if (skipBCost >= 0) {
				pairs.add(new int[] {-1, j});
				j++;
				continue;
			}

			// We count a replacement as no match, since this is the expected behavior for
			// the current uses of this funciton, though it may be a bit unusual
			pairs.add(new int[] {-1, j});
			pairs.add(new int[] {i, -1});
			i++; j++;

			// Don't see why this should ever need to happen. It means none of the above worked
			// which can only happen in a) an invalid configuration or b) skipping a replacement
			// and in neither case should we another pair
//			if (i < sequenceA.length && j < sequenceB.length) pairs.add(new int[] {i, j});
		}

		while (i < sequenceA.length) {
			pairs.add(new int[] {i, -1});
			i++;
		}

		while (j < sequenceB.length) {
			pairs.add(new int[] {-1, j});
			j++;
		}

		return pairs;
	}

	public static String[] smartScriptEdit(String[] sequenceA, String[] sequenceB) {
		List<int[]> pairs = alignPairs(sequenceA, sequenceB, 1, 1, 100);
//		for (int[] pair : pairs) {
//			String a = pair[0] == -1 ? null : sequenceA[pair[0]];
//			String b = pair[1] == -1 ? null : sequenceB[pair[1]];
//			System.out.println(a + " <-> " + b);
//		}
		List<String> sequence = new ArrayList<>();
		for (String s : sequenceA) sequence.add(s);
		if (!doEdit(MoveEditor, sequenceA, sequenceB, pairs, sequence)) {
			if (!doEdit(AddEditor, sequenceA, sequenceB, pairs, sequence)) {
				if (!doEdit(DeleteEditor, sequenceA, sequenceB, pairs, sequence)) {
					boolean same = true;
					if (sequence.size() != sequenceB.length) same = false;
					else {
						for (int i = 0; i < sequenceB.length; i++) {
							if (!sequence.get(i).equals(sequenceB[i])) same = false;
						}
					}
					if (!same) {
						throw new RuntimeException("Failed to edit");
					}
					return null;
				}
			}
		}
		return sequence.toArray(new String[sequence.size()]);
	}

	public static int doEdits(List<String> sequence, String[] sequenceB, Editor editor) {
		return doEdits(sequence, sequenceB, editor, Integer.MAX_VALUE);
	}

	public static int doEdits(List<String> sequence, String[] sequenceB, Editor editor,
			int maxEdits) {
		if (maxEdits <= 0) return 0;
		int edits = 0;
		while (edits < maxEdits) {
			String[] sequenceA = sequence.toArray(new String[sequence.size()]);
			List<int[]> pairs = alignPairs(sequenceA, sequenceB, 1, 1, 100);
			if (!doEdit(editor, sequenceA, sequenceB, pairs, sequence)) break;
			edits++;
		}
		return edits;
	}

	private static boolean doEdit(Editor editor, String[] sequenceA, String[] sequenceB,
			List<int[]> pairs, List<String> sequence) {
		Edit edit = editor.getEdit(sequenceA, sequenceB, pairs, sequence);
		if (edit != null) {
			edit.edit(sequence);
			return true;
		}
		return false;
	}

	public interface Editor {
		Edit getEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence);
	}

	public static Editor MoveEditor = new Editor() {
		@Override
		public Edit getEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs,
				List<String> sequence) {
			return moveEdit(sequenceA, sequenceB, pairs, sequence);
		}
	};

	public static Editor AddEditor = new Editor() {
		@Override
		public Edit getEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs,
				List<String> sequence) {
			return addEdit(sequenceA, sequenceB, pairs, sequence);
		}
	};

	public static Editor DeleteEditor = new Editor() {
		@Override
		public Edit getEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs,
				List<String> sequence) {
			return deleteEdit(sequenceA, sequenceB, pairs, sequence);
		}
	};

	public interface Edit {
		void edit(List<String> sequence);
	}

	public static class MoveEdit implements Edit {
		public final int from, to;

		public MoveEdit(int from, int to) {
			this.from = from;
			this.to = to;
		}

		@Override
		public void edit(List<String> sequence) {
			String item = sequence.remove(from);
			int addIndex = to;
			if (from < to) addIndex--;
			sequence.add(addIndex, item);
		}
	}

	public static class AddEdit implements Edit {
		public final String item;
		public final int index;

		public AddEdit(String item, int index) {
			this.item = item;
			this.index = index;
		}

		@Override
		public void edit(List<String> sequence) {
			sequence.add(index, item);
		}
	}

	public static class DeleteEdit implements Edit {
		public final int index;

		public DeleteEdit(int index) {
			this.index = index;
		}

		@Override
		public void edit(List<String> sequence) {
			sequence.remove(index);
		}
	}

	private static MoveEdit moveEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs,
			List<String> sequence) {
		for (int[] pair : pairs) {
			if (pair[1] == -1) {
				int toMove = pair[0];
				String toMatch = sequenceA[toMove];
				for (int[] p : pairs) {
					if (p[0] == -1) {
						String match = sequenceB[p[1]];
						if (!match.equals(toMatch)) continue;
						int closestA = correspondingIndexBefore(pairs, sequence, p[1]);
						return new MoveEdit(toMove, closestA + 1);
//						if (closestA < toMove) {
//							sequence.remove(toMove);
//							sequence.add(closestA + 1, toMatch);
//						} else {
//							sequence.add(closestA + 1, toMatch);
//							sequence.remove(toMove);
//						}
//						return true;
					}
				}
			}
		}
		return null;
	}

	private static DeleteEdit deleteEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs,
			List<String> sequence) {
		for (int[] pair : pairs) {
			if (pair[1] == -1) {
				String toRemove = sequenceA[pair[0]];
				int totalA = 0, totalB = 0;
				for (String a : sequenceA) if (a.equals(toRemove)) totalA++;
				for (String b : sequenceB) if (b.equals(toRemove)) totalB++;
				if (totalA > totalB) {
					return new DeleteEdit(pair[0]);
//					sequence.remove(pair[0]);
//					return true;
				}
			}
		}
		return null;
	}

	private static AddEdit addEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs,
			List<String> sequence) {
		for (int[] pair : pairs) {
			if (pair[0] == -1) {
				String toAdd = sequenceB[pair[1]];
				int totalA = 0, totalB = 0;
				for (String a : sequenceA) if (a.equals(toAdd)) totalA++;
				for (String b : sequenceB) if (b.equals(toAdd)) totalB++;
				if (totalA < totalB) {
					int toSearch = pair[1];
					int closestA = correspondingIndexBefore(pairs, sequence, toSearch);
					return new AddEdit(toAdd, closestA + 1);
//					sequence.add(closestA + 1, toAdd);
//					return true;
				}
			}
		}
		return null;
	}

	private static int correspondingIndexBefore(List<int[]> pairs, List<String> sequence,
			int bIndex) {
		int closestB = -1;
		int closestA = -1;
		for (int[] p : pairs) {
			int pa = p[0], pb = p[1];
			if (pa >= 0 && pb >= 0 && pb < bIndex && pb > closestB) {
				closestB = pb;
				closestA = pa;
			}
		}
		return closestA;
	}

	private static int[][] createAlignmentMatrix(String[] sequenceA, String[] sequenceB,
			int insCost, int delCost, int subCost, boolean flipInvalid) {
		// The penalties to apply
		int matchCost = 0;
		int aLength = sequenceA.length, bLength = sequenceB.length;

		int[][] opt = new int[aLength + 1][bLength + 1];

		// First of all, compute insertions and deletions at 1st row/column
		for (int i = 1; i <= aLength; i++)
		    opt[i][0] = opt[i - 1][0] + delCost;
		for (int j = 1; j <= bLength; j++)
		    opt[0][j] = opt[0][j - 1] + insCost;

		for (int i = 1; i <= aLength; i++) {
		    for (int j = 1; j <= bLength; j++) {
		        int scoreDiag = opt[i - 1][j - 1] +
		                (sequenceA[i-1].equals(sequenceB[j-1]) ?
		                    matchCost : // same symbol
		                    subCost); // different symbol
		        int scoreLeft = opt[i][j - 1] + insCost; // insertion
		        int scoreUp = opt[i - 1][j] + delCost; // deletion
		        // we take the minimum
		        opt[i][j] = Math.min(Math.min(scoreDiag, scoreLeft), scoreUp);
		    }
		}

		if (flipInvalid) {
			for (int i = aLength - 1; i >= 0; i--) {
				if (opt[i + 1][bLength] - opt[i][bLength] != delCost) {
					opt[i][bLength] *= -1;
				}
			}
			for (int j = bLength - 1; j >= 0; j--) {
				if (opt[aLength][j + 1] - opt[aLength][j] != insCost) {
					opt[aLength][j] *= -1;
				}
			}
			for (int i = aLength - 1; i >= 0; i--) {
				for (int j = bLength - 1; j >= 0; j--) {
					int value = opt[i][j];
					int skipI = opt[i + 1][j];
					int skipJ = opt[i][j + 1];
					int sub = opt[i + 1][j + 1];
					boolean equal = sequenceA[i].equals(sequenceB[j]);

					if (skipI - value == delCost) continue;
					if (skipJ - value == insCost) continue;
					if (equal && sub == value) continue;
					if (!equal && sub - value == subCost) continue;

					opt[i][j] *= -1;
				}
			}
		}

		return opt;
	}

	public static int[] reorderIndices(String[] from, String[] to, int[] toOrderGroups) {
		int[] toIndices = new int[to.length];
		// Get the raw to-indices (the cost doesn't matter, so we just use 1s)
		getProgress(from, to, toOrderGroups, 1, 1, 1, toIndices);

		// Find any unused indices in the toIndices array and set them sequentially

		// First create an array of all the unused indices, which will be out of order
		int[] unusedIndices = new int[toIndices.length];
		Arrays.fill(unusedIndices, Integer.MAX_VALUE);
		int index = 0;
		for (int i = 0; i < unusedIndices.length; i++) {
			boolean found = false;
			for (int j = 0; j < toIndices.length; j++) {
				if (toIndices[j] == i) {
					found = true;
					break;
				}
			}
			if (!found) {
				unusedIndices[index++] = i;
			}
		}

		// Then sort it to be in order, will the blanks (MAX_VALUE) at the end
		Arrays.sort(unusedIndices);

		// Then fill in the unused indices in toIndices with the sequential missing values
		index = 0;
		for (int i = 0; i < toIndices.length; i++) {
			if (toIndices[i] == -1) {
				toIndices[i] = unusedIndices[index++];
			}
		}

		return toIndices;
	}

	public static int getProgress(String[] from, String[] to, int orderReward, int unorderReward) {
		return (int) Math.round(getProgress(from, to, orderReward, unorderReward, 0));
	}

	public static double getProgress(String[] from, String[] to, int orderReward, int unorderReward,
			double skipCost) {
		return getProgress(from, to, null, orderReward, unorderReward, skipCost);
	}

	public static double getProgress(String[] from, String[] to, int[] toOrderGroups,
			int orderReward, int unorderReward, double skipCost) {

		int[] toIndices = new int[to.length];
		return getProgress(from, to, toOrderGroups, orderReward, unorderReward, skipCost,
				toIndices);
	}

	private static double getProgress(String[] from, String[] to, int[] toOrderGroups,
			int orderReward, int unorderReward, double skipCost, int[] toIndices) {
		// TODO: This can and should be much more efficient
		List<String> toList = new LinkedList<>(Arrays.asList(to));

		int[] indices = new int[from.length];
		for (int i = 0; i < from.length; i++) {
			String item = from[i];
			int index = toList.indexOf(item);
			if (index >= 0) {
				toList.set(index, "\0");
				indices[i] = index;
			} else {
				indices[i] = -1;
			}
		}

		Arrays.fill(toIndices, -1);

		double reward = 0;
		int lastIndex = -1;
		int maxIndex = -1;
		for (Integer index : indices) {
			if (index < 0) continue;
			int adjIndex = index;
			int group;
//			System.out.println(index);
			if (toOrderGroups != null && (group = toOrderGroups[adjIndex]) > 0) {
				// If the matched "to" item is in an order group (for which all items in the group
				// are unordered), we should match i to the index of the earliest item in this group
				// which comes after the last index, since the actual match could have been
				// reordered to that index without loss of meaning

				// First check if the index can be decreased within the order group without going
				// <= the max seen index (to avoid duplicate adjusted indices)
				while (adjIndex > 0 && adjIndex - 1 > maxIndex &&
						toOrderGroups[adjIndex - 1] == group) {
					adjIndex--;
//					System.out.println("m-> " + adjIndex);
				}
				// Next check if the index is out of order and increasing it to maxIndex + 1 will
				// make in order
				int nextIndex = maxIndex + 1;
				if (nextIndex < toOrderGroups.length && adjIndex <= lastIndex  &&
						toOrderGroups[nextIndex] == group) {
					adjIndex = nextIndex;
//					System.out.println("p-> " + adjIndex);
				}

				// Set the actual to-index used after adjustments above
				if (index != adjIndex) {
					toIndices[index] = adjIndex;
				}
			}

			if (to[adjIndex] != null) {
				reward += adjIndex > lastIndex ? orderReward : unorderReward;
				// If the index is more than 1 more than the last index, we've skipped some indices,
				// so we add the skip penalty (if any)
				reward -= Math.max(0, adjIndex - lastIndex - 1) * skipCost;
			}
			lastIndex = adjIndex;
			maxIndex = Math.max(maxIndex, adjIndex);
		}

		return reward;
	}

	public static int getMissingNodeCount(String[] from, String[] to) {
		return to.length - getProgress(to, from, 1, 1);
	}

	public static void main(String[] args) {
//		List<int[]> pairs = alignPairs(new String[] {
//				"a", "b", "c"
//		}, new String[] {
//				"a", "c", "b"
//		}, 2, 2, 3);
//
//		System.out.println();
//		for (int[] pair : pairs) {
//			System.out.println(Arrays.toString(pair));
//		}

		String[] from = new String[] {
				"a", "b",
		}, to = new String[] {
				"a", "c", "b", "d", "e"
//				"a", "b", "c", "a", "d", "e"
		};

		System.out.println(getProgress(from, to, 2, 1, 0.5));
		System.out.println(getMissingNodeCount(from, to) * 0.25);

//		System.out.println(Arrays.toString(reorderIndices(new String[] {
//				"c", "d", "e", "f"
//		}, new String[] {
//				"c", "f", "d", "e", "g"
//		}, new int[] {
//				0, 1, 1, 1, 0
//		})));
	}
}
