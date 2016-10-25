package edu.isnap.datasets.run;

import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Spring2016;
import edu.isnap.parser.Store.Mode;

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




