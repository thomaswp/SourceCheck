package edu.isnap.datasets.run;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

import edu.isnap.ctd.hint.HintGenerator;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Spring2016;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.parser.Store.Mode;

// TODO: This class, and all Snap-specific files should be moved into a separate project (or just
// the Parser project) and this Project should be saved for generic Hint Generation code, which can
// be used by the Snap-specific code here as a library.

/**
 * Class for constructing HintGenerators from Snap data and caching them for later use.
 */
public class RunHintBuilder {

	public static void main(String[] args) throws IOException, InstantiationException,
			IllegalAccessException, ClassNotFoundException, SQLException {
		// Optionally, clean out all cached assignments first
//		SnapParser.clean(Assignment.Spring2016.dataDir);

		// Builds and caches a HintGenerator for each of these assignments
		buildHints(Spring2016.PolygonMaker, 1);
		buildHints(Spring2016.Squiral, 1);
		buildHints(Spring2016.GuessingGame1, 1);
		buildHints(Spring2016.GuessingGame2, 1);
		// Then copies the cache to the HintServer
		RunCopyData.copyGraphs(Spring2016.dataDir);

//		buildHints(Assignment.HelpSeeking.BrickWall, 1);
//		CopyData.copyGraphs(Assignment.HelpSeeking.BrickWall.dataDir);
	}


	/**
	 * Builds and caches a {@link HintGenerator} for the given assignment, using only data with
	 * the supplied minGrade.
	 */
	private static void buildHints(Assignment assignment, double minGrade)
			throws FileNotFoundException {
		System.out.println("Loading: " + assignment.name);
		SnapHintBuilder subtree = new SnapHintBuilder(assignment);
		subtree.nodeMap();
		System.out.print("Building subtree: ");
		long ms = System.currentTimeMillis();
		HintGenerator builder = subtree.buildGenerator(Mode.Overwrite, minGrade);
		String dir = String.format("%s/graphs/%s-g%03d/", assignment.dataDir,
				assignment.name, Math.round(minGrade * 100));
		builder.saveGraphs(dir, 1);
		System.out.println((System.currentTimeMillis() - ms) + "ms");
	}
}
