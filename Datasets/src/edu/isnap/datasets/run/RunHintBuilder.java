package edu.isnap.datasets.run;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.ctd.hint.feature.Feature;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.parser.Store.Mode;

/**
 * Class for constructing HintGenerators from Snap data and caching them for later use.
 */
public class RunHintBuilder {

	public static void main(String[] args) throws IOException, InstantiationException,
			IllegalAccessException, ClassNotFoundException, SQLException {
		// Optionally, clean out all cached assignments first
//		SnapParser.clean(Spring2016.dataDir);

		// Builds and caches a HintGenerator for each of these assignments
//		buildHints(CSC200.PolygonMaker, 1);
		buildHints(CSC200.Squiral, 1);
//		buildHints(CSC200.GuessingGame1, 1);
//		buildHints(CSC200.GuessingGame2, 1);
//		buildHints(CSC200.GuessingGame3, 1);
		// Then copies the cache to the HintServer
//		RunCopyData.copyHintDatabaseToServer(CSC200.dataDir);

//		buildHints(Fall2016.Squiral, 1);

//		buildHints(HelpSeekingExperts.BrickWall, 1);
//		RunCopyData.copyHintDatabaseToServer(HelpSeekingExperts.BrickWall.dataDir);
//		buildHints(Spring2016.GuessingGame1, 1);
//		RunCopyData.copyHintDatabaseToServer(Spring2016.GuessingGame1.dataDir, "spring2016");
	}


	/**
	 * Builds and caches a {@link HintMapBuilder} for the given assignment, using only data with
	 * the supplied minGrade.
	 */
	@SuppressWarnings("unchecked")
	public static void buildHints(Assignment assignment, double minGrade)
			throws FileNotFoundException {
		System.out.println("Loading: " + assignment.name);
		HintConfig config = ConfigurableAssignment.getConfig(assignment);

		File featuresFile = new File(assignment.featuresFile());
		List<Feature> features = null;
		if (featuresFile.exists()) {
			Kryo kryo = new Kryo();
			Input input = new Input(new FileInputStream(featuresFile));
			features = kryo.readObject(input, ArrayList.class);
			input.close();
		}

		SnapHintBuilder subtree = new SnapHintBuilder(assignment, new HintMap(config, features));
		// Load the nodeMap so as no to throw off timing
		subtree.nodeMap();
		System.out.print("Building subtree: ");
		long ms = System.currentTimeMillis();
		HintMapBuilder builder = subtree.buildGenerator(Mode.Overwrite, minGrade);
		int nAttempts = builder.hintMap.solutions.size();
		System.out.println((System.currentTimeMillis() - ms) + "ms; " + nAttempts + " attempts");
		String dir = String.format("%s/graphs/%s-g%03d/", assignment.dataDir,
				assignment.name, Math.round(minGrade * 100));
		builder.hintMap.saveGraphs(dir, 1);
	}
}
