package com.snap.graph;

import java.util.Arrays;

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
	
	public static String[] makeAlignmentEdits(int maxEdits, String[] sequenceA, String[] sequenceB, int gapCost, int subCost) {
		int[][] opt = createAlignmentMatrix(sequenceA, sequenceB, gapCost, subCost);
		for (int[] row : opt) {
			System.out.println(Arrays.toString(row));
		}
		int i = sequenceA.length - 1, j = sequenceB.length - 1;
		String out = "";
		while (i > 0 && j > 0) {
			int cost = opt[i][j];
			int replaceCost = opt[i-1][j-1];
			if (replaceCost == cost) {
				i--; j--;
				out = sequenceA[i] + out;
				continue;
			}
			
			int skipACost = opt[i-1][j];
			int skipBCost = opt[i][j-1];
			
			int min = Math.min(replaceCost, Math.min(skipACost, skipBCost));
			if (skipBCost - cost == 1 && skipBCost == min) {
				j--;
				continue;
			}
		}
		return null;
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
