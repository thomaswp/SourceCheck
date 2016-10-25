package com.snap.graph.subtree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.Node;
import com.snap.parser.Assignment;
import com.snap.parser.AssignmentAttempt;
import com.snap.parser.AttemptAction;
import com.snap.parser.Grade;
import com.snap.parser.Store;
import com.snap.parser.Store.Mode;

// TODO: This class, and all Snap-specific files should be moved into a separate project (or just
// the Parser project) and this Project should be saved for generic Hint Generation code, which can
// be used by the Snap-specific code here as a library.

/**
 * Class for constructing HintGenerators from Snap data and caching them for later use.
 */
public class SnapHintBuilder {

	public static void main(String[] args) throws IOException, InstantiationException,
			IllegalAccessException, ClassNotFoundException, SQLException {
		// Optionally, clean out all cached assignments first
//		SnapParser.clean(Assignment.Spring2016.dataDir);

		// Builds and caches a HintGenerator for each of these assignments
		buildHints(Assignment.Spring2016.PolygonMaker, 1);
		buildHints(Assignment.Spring2016.Squiral, 1);
		buildHints(Assignment.Spring2016.GuessingGame1, 1);
		buildHints(Assignment.Spring2016.GuessingGame2, 1);
		// Then copies the cache to the HintServer
		CopyData.copyGraphs(Assignment.Spring2016.dataDir);

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


	public final Assignment assignment;
	private final HintMap hintMap;
	private final HashMap<String, HintMap> studentSubtreeCache =
			new HashMap<>();

	private Map<String, List<Node>> nodeMapCache;
	private Map<String, Grade> gradeMapCache;

	/**
	 * Gets a map of attemptIDs to Node lists, where each list of Nodes represents
	 * the ASTs for the Snapshots created during a given attempt, with the last one being the
	 * submitted AST.
	 */
	public Map<String, List<Node>> nodeMap() {
		if (nodeMapCache == null) {
			try {
				parseAttempts();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodeMapCache;
	}

	/**
	 * Gets a map of attemptIDs to grades for the submitted Snapshot of that attempt.
	 */
	public Map<String, Grade> gradeMap() {
		if (gradeMapCache == null) {
			try {
				parseAttempts();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return gradeMapCache;
	}

	/**
	 * Creates SnapHintBuilder for the given assignment.
	 * @param assignment
	 */
	public SnapHintBuilder(Assignment assignment) {
		this(assignment, new HintFactoryMap(assignment.config()));
	}

	public SnapHintBuilder(Assignment assignment, HintMap hintMap) {
		this.assignment = assignment;
		this.hintMap = hintMap;
	}

	/**
	 * Builds a HintGenerator with all attempts for this assignment.
	 * @param storeMode
	 * @return
	 */
	public HintGenerator buildGenerator(Mode storeMode) {
		return buildGenerator(storeMode, 0);
	}

	/**
	 * Builds a HintGenerator with all attempts for this assignment that have at least the given
	 * minGrade.
	 * @param storeMode
	 * @param minGrade
	 * @return
	 */
	public HintGenerator buildGenerator(Mode storeMode, final double minGrade) {
		String storePath = new File(assignment.dataDir, String.format("%s-g%03d.cached",
				assignment.name, Math.round(minGrade * 100))).getAbsolutePath();
		HintGenerator builder = Store.getCachedObject(HintGenerator.getKryo(),
				storePath, HintGenerator.class, storeMode,
				new Store.Loader<HintGenerator>() {
			@Override
			public HintGenerator load() {
				return buildGenerator((String)null, minGrade);
			}
		});
		return builder;
	}

	/**
	 * Builds a HintGenerator with all attempts for this assignment that have at least the given
	 * minGrade, excluding data from the given testAttempt. This is generally used when using
	 * leave-one-out cross-validation, to generate hints using all students except the one for
	 * which hints are being generated.
	 * @param storeMode
	 * @param minGrade
	 * @return
	 */
	public HintGenerator buildGenerator(String testAttempt, double minGrade) {
		final HintGenerator builder = new HintGenerator(hintMap.instance(), minGrade);
		builder.startBuilding();
		final AtomicInteger count = new AtomicInteger();
		for (String student : nodeMap().keySet()) {
			if (student.equals(testAttempt)) continue;

			Grade grade = gradeMapCache.get(student);
			if (grade != null && grade.average() < minGrade) continue;

			final List<Node> nodes = nodeMap().get(student);
			if (nodes.size() == 0) continue;

			final String fStudent = student;

			count.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					HintMap studentMap;
					synchronized (studentSubtreeCache) {
						studentMap = studentSubtreeCache.get(fStudent);
					}
					if (studentMap == null) {
//						System.out.println(fStudent);
						studentMap = builder.addAttempt(nodes, assignment.hasIDs);
						synchronized (studentSubtreeCache) {
							studentSubtreeCache.put(fStudent, studentMap);
						}
					} else {
						builder.addAttemptMap(studentMap);
					}
					count.decrementAndGet();
				}
			}).run(); // Threading this causes bugs
		}
		while (count.get() != 0) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		builder.finishedAdding();
		return builder;
	}

	// Parses all attempts for this assignment
	private void parseAttempts() throws IOException {
		Map<String, AssignmentAttempt> students = assignment.load(Mode.Use, true);
		nodeMapCache = new TreeMap<>();
		gradeMapCache = new TreeMap<>();

		for (String attemptID : students.keySet()) {
			AssignmentAttempt attempt = students.get(attemptID);
			if (!attempt.exported) continue;
			if (attempt.submittedActionID == AssignmentAttempt.NOT_SUBMITTED) continue;
			List<Node> nodes = new ArrayList<>();

			for (AttemptAction row : attempt) {
				Node node = SimpleNodeBuilder.toTree(row.snapshot, true);
				nodes.add(node);
			}

			if (nodes.size() == 0) continue;
			if (assignment.graded && attempt.grade == null) {
				System.err.println("No grade for: " + attemptID);
			}

			nodeMapCache.put(attemptID, nodes);
			gradeMapCache.put(attemptID, attempt.grade);
		}
	}
}
