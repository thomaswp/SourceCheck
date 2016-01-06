package com.snap.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Alignment {
	public static double normalizedAlignScore(String[] sequenceA, String[] sequenceB) {
		int cost = alignCost(sequenceA, sequenceB);
		int maxLength = Math.max(sequenceA.length, sequenceB.length);
		int aligned = maxLength - cost;
		return (double) (aligned + 1) / (maxLength + 1);
	}
	
	public static int alignCost(String[] sequenceA, String[] sequenceB) {
		return alignCost(sequenceA, sequenceB, 1, 1);
	}
	
	public static double normAlignCost(String[] sequenceA, String[] sequenceB, int gapCost, int subCost) {
		int cost = alignCost(sequenceA, sequenceB, gapCost, subCost);
		int length = Math.max(sequenceA.length, sequenceB.length);
		return length == 0 ? 0 : ((double) cost / length);
	}
	
	// Credit: http://introcs.cs.princeton.edu/java/96optimization/Diff.java.html
	public static int alignCost(String[] sequenceA, String[] sequenceB, int gapCost, int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, gapCost, subCost);
		return opt[sequenceA.length][sequenceB.length];
	}
	
	public static List<int[]> alignPairs(String[] sequenceA, String[] sequenceB, int gapCost, int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, gapCost, subCost);
		ArrayList<int[]> pairs = new ArrayList<int[]>();
//		
//		for (int[] row : opt) {
//			System.out.println(Arrays.toString(row));
//		}
		int i = sequenceA.length, j = sequenceB.length;
		while (i > 0 && j > 0) {
			int cost = opt[i][j];
//			int replaceCost = opt[i-1][j-1];
			int skipACost = opt[i-1][j];
			int skipBCost = opt[i][j-1];
//			int min = Math.min(replaceCost, Math.min(skipACost, skipBCost));
			
			if (sequenceA[i-1].equals(sequenceB[j-1])) {
				i--; j--;
				pairs.add(new int[] {i, j});
				continue;
			}
			
			if (cost - skipBCost == gapCost && skipBCost <= skipACost) {
				j--;
				pairs.add(new int[] {-1, j});
				continue;
			}
			
			if (cost - skipACost == gapCost) {
				i--;
				pairs.add(new int[] {i, -1});
				continue;
			}

			i--; j--;
			pairs.add(new int[] {i, j});
		}
		
		while (i > 0) {
			i--;
			pairs.add(new int[] {i, -1});	
		}
		
		while (j > 0) {
			j--;
			pairs.add(new int[] {-1, j});	
		}
		
		Collections.reverse(pairs);
		return pairs;
		
	}
	
	public static String[] smartScriptEdit(String[] sequenceA, String[] sequenceB) {
		List<int[]> pairs = alignPairs(sequenceA, sequenceB, 1, 100);
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

	private static int[][] createAlignmentMatrix(String[] sequenceA, String[] sequenceB, int gapCost, int subCost) {
		// The penalties to apply
		int matchCost = 0;

		int[][] opt = new int[sequenceA.length + 1][sequenceB.length + 1];

		// First of all, compute insertions and deletions at 1st row/column
		for (int i = 1; i <= sequenceA.length; i++)
		    opt[i][0] = opt[i - 1][0] + gapCost;
		for (int j = 1; j <= sequenceB.length; j++)
		    opt[0][j] = opt[0][j - 1] + gapCost;

		for (int i = 1; i <= sequenceA.length; i++) {
		    for (int j = 1; j <= sequenceB.length; j++) {
		        int scoreDiag = opt[i - 1][j - 1] +
		                (sequenceA[i-1].equals(sequenceB[j-1]) ?
		                    matchCost : // same symbol
		                    subCost); // different symbol
		        int scoreLeft = opt[i][j - 1] + gapCost; // insertion
		        int scoreUp = opt[i - 1][j] + gapCost; // deletion
		        // we take the minimum
		        opt[i][j] = Math.min(Math.min(scoreDiag, scoreLeft), scoreUp);
		    }
		}
		return opt;
	}
}
