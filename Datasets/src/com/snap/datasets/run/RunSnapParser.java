package com.snap.datasets.run;

import com.snap.parser.Assignment;
import com.snap.parser.Spring2016;
import com.snap.parser.Store.Mode;

/**
 * Parser for iSnap logs.
 */
public class RunSnapParser {

	public static void main(String[] args) {
		// Reloads and caches assignments for a give dataset.

		// Replace Fall2015 with the dataset to reload.
		for (Assignment assignment : Spring2016.All) {
			System.out.println("Reparsing: " + assignment);
			// Loads the given assignment, overwriting any cached data.
			assignment.load(Mode.Overwrite, true);
		}
	}
}




