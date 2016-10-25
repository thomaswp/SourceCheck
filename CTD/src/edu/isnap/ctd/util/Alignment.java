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

	public static double normAlignCost(String[] sequenceA, String[] sequenceB, int insCost, int delCost, int subCost) {
		int cost = alignCost(sequenceA, sequenceB, insCost, delCost, subCost);
		int length = Math.max(sequenceA.length, sequenceB.length);
		return length == 0 ? 0 : ((double) cost / length);
	}

	// Credit: http://introcs.cs.princeton.edu/java/96optimization/Diff.java.html
	public static int alignCost(String[] sequenceA, String[] sequenceB, int insCost, int delCost, int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, insCost, delCost, subCost, false);
		return opt[sequenceA.length][sequenceB.length];
	}

	public static List<int[]> alignPairs(String[] sequenceA, String[] sequenceB, int insCost, int delCost, int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, insCost, delCost, subCost, true);
		ArrayList<int[]> pairs = new ArrayList<int[]>();
//
//		for (int[] row : opt) {
//			System.out.println(Arrays.toString(row));
//		}
		int i = 0, j = 0;
		while (i < sequenceA.length && j < sequenceB.length) {
			int replaceCost = opt[i+1][j+1];
			int skipACost = opt[i+1][j];
			int skipBCost = opt[i][j+1];
//			int min = Math.min(replaceCost, Math.min(skipACost, skipBCost));

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

			i++; j++;
			pairs.add(new int[] {i, j});
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
		List<String> sequence = new ArrayList<String>();
		for (String s : sequenceA) sequence.add(s);
		if (!moveEdit(sequenceA, sequenceB, pairs, sequence)) {
			if (!addEdit(sequenceA, sequenceB, pairs, sequence)) {
				if (!deleteEdit(sequenceA, sequenceB, pairs, sequence)) {
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
		return doEdits(sequence, sequenceB, editor, -1);
	}

	public static int doEdits(List<String> sequence, String[] sequenceB, Editor editor, int maxEdits) {
		if (maxEdits <= 0) return 0;
		int edits = 0;
		while (maxEdits < 0 || edits < maxEdits) {
			String[] sequenceA = sequence.toArray(new String[sequence.size()]);
			List<int[]> pairs = alignPairs(sequenceA, sequenceB, 1, 1, 100);
			if (!editor.edit(sequenceA, sequenceB, pairs, sequence)) break;
			edits++;
		}
		return edits;
	}

	public interface Editor {
		boolean edit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence);
	}

	public static Editor MoveEditor = new Editor() {
		@Override
		public boolean edit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence) {
			return moveEdit(sequenceA, sequenceB, pairs, sequence);
		}
	};

	public static Editor AddEditor = new Editor() {
		@Override
		public boolean edit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence) {
			return addEdit(sequenceA, sequenceB, pairs, sequence);
		}
	};

	public static Editor DeleteEditor = new Editor() {
		@Override
		public boolean edit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence) {
			return deleteEdit(sequenceA, sequenceB, pairs, sequence);
		}
	};

	private static boolean moveEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence) {
		for (int[] pair : pairs) {
			if (pair[1] == -1) {
				int toMove = pair[0];
				String toMatch = sequenceA[toMove];
				for (int[] p : pairs) {
					if (p[0] == -1) {
						String match = sequenceB[p[1]];
						if (!match.equals(toMatch)) continue;
						int closestA = correspondingIndexBefore(pairs, sequence, p[1]);
						if (closestA < toMove) {
							sequence.remove(toMove);
							sequence.add(closestA + 1, toMatch);
						} else {
							sequence.add(closestA + 1, toMatch);
							sequence.remove(toMove);
						}
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean deleteEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence) {
		for (int[] pair : pairs) {
			if (pair[1] == -1) {
				String toRemove = sequenceA[pair[0]];
				int totalA = 0, totalB = 0;
				for (String a : sequenceA) if (a.equals(toRemove)) totalA++;
				for (String b : sequenceB) if (b.equals(toRemove)) totalB++;
				if (totalA > totalB) {
					sequence.remove(pair[0]);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean addEdit(String[] sequenceA, String[] sequenceB, List<int[]> pairs, List<String> sequence) {
		for (int[] pair : pairs) {
			if (pair[0] == -1) {
				String toAdd = sequenceB[pair[1]];
				int totalA = 0, totalB = 0;
				for (String a : sequenceA) if (a.equals(toAdd)) totalA++;
				for (String b : sequenceB) if (b.equals(toAdd)) totalB++;
				if (totalA < totalB) {
					int toSearch = pair[1];
					int closestA = correspondingIndexBefore(pairs, sequence, toSearch);
					sequence.add(closestA + 1, toAdd);
					return true;
				}
			}
		}
		return false;
	}

	private static int correspondingIndexBefore(List<int[]> pairs, List<String> sequence, int bIndex) {
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

	private static int[][] createAlignmentMatrix(String[] sequenceA, String[] sequenceB, int insCost, int delCost, int subCost, boolean flipInvalid) {
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

	public static int getProgress(String[] from, String[] to, int orderReward, int unorderReward) {
		List<String> fromList = new LinkedList<>(Arrays.asList(from));
		List<String> toList = new LinkedList<>(Arrays.asList(to));

		List<Integer> indices = new LinkedList<>();

		while (!fromList.isEmpty()) {
			String item = fromList.remove(0);
			int index = toList.indexOf(item);
			if (index >= 0) {
				toList.set(index, null);
				indices.add(index);
			}
		}

		int reward = 0;
		int lastIndex = -1;
		for (Integer i : indices) {
			reward += i > lastIndex ? orderReward : unorderReward;
			lastIndex = i;
		}

		return reward;
	}

	public static void main(String[] args) {
		System.out.println(getProgress(new String[] {
				"b", "a", "d"
		}, new String[] {
				"d", "a", "b",
		}, 2, 1));
	}
}
