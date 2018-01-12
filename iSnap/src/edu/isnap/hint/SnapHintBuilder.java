package edu.isnap.hint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.kryo.Kryo;

import edu.isnap.ctd.graph.Graph;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.vector.IndexedVectorState;
import edu.isnap.ctd.graph.vector.VectorGraph;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.ctd.util.StringHashable;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Grade;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store;
import edu.isnap.parser.Store.Mode;


/**
 * Class for constructing HintGenerators from Snap data and caching them for later use.
 */
public class SnapHintBuilder {

	public final Assignment assignment;
	private final HintMap hintMap;
	private final HashMap<String, HintMap> studentSubtreeCache =
			new HashMap<>();

	private Map<String, LoadedAttempt> nodeMapCache;

	/**
	 * Gets a map of attemptIDs to Node lists, where each list of Nodes represents
	 * the ASTs for the Snapshots created during a given attempt, with the last one being the
	 * submitted AST.
	 */
	public Map<String, LoadedAttempt> nodeMap() {
		if (nodeMapCache == null) {
			try {
				nodeMapCache = parseAttempts();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodeMapCache;
	}

	/**
	 * Creates SnapHintBuilder for the given assignment.
	 * @param assignment
	 */
	public SnapHintBuilder(Assignment assignment) {
		this(assignment, new HintMap(assignment instanceof Configurable ?
				((Configurable) assignment).getConfig() : new SnapHintConfig()));
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
	public HintMapBuilder buildGenerator(Mode storeMode) {
		return buildGenerator(storeMode, 0);
	}

	/**
	 * Builds a HintGenerator with all attempts for this assignment that have at least the given
	 * minGrade.
	 * @param storeMode
	 * @param minGrade
	 * @return
	 */
	public HintMapBuilder buildGenerator(Mode storeMode, final double minGrade) {
		String storePath = getStorePath(assignment, minGrade);
		HintMapBuilder builder = Store.getCachedObject(getKryo(),
				storePath, HintMapBuilder.class, storeMode,
				new Store.Loader<HintMapBuilder>() {
			@Override
			public HintMapBuilder load() {
				return buildGenerator((String)null, minGrade);
			}
		});
		return builder;
	}

	public static String getStorePath(Assignment assignment, double minGrade) {
		return getStorePath(assignment.dataDir, assignment.name, minGrade);
	}

	public static String getStorePath(String baseDir, String assignmentName, double minGrade) {
		return getStorePath(baseDir, assignmentName, minGrade, null);
	}

	public static String getStorePath(String baseDir, String assignmentName, double minGrade,
			String dataset) {
		return new File(baseDir, String.format("%s-g%03d%s.cached",
				assignmentName, Math.round(minGrade * 100),
				dataset == null ? "" : ("-" + dataset))).getAbsolutePath();
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
	public HintMapBuilder buildGenerator(String testAttempt, double minGrade) {
		HintConfig config = hintMap.getHintConfig();
		final HintMapBuilder builder = new HintMapBuilder(hintMap.instance(), minGrade);
		builder.startBuilding();
		final AtomicInteger count = new AtomicInteger();
		for (String student : nodeMap().keySet()) {
			if (student.equals(testAttempt)) continue;

			final LoadedAttempt nodes = nodeMap().get(student);

			if (config.requireGrade && nodes.grade == null) continue;
			if (nodes.grade != null && nodes.grade.average() < minGrade) continue;

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
						studentMap = builder.addAttempt(nodes, nodes.hasIDs);
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
	private Map<String, LoadedAttempt> parseAttempts() throws IOException {
		Map<String, AssignmentAttempt> students = assignment.load(Mode.Use, true, true,
				new SnapParser.LikelySubmittedOnly());
		Map<String, LoadedAttempt> nodeMapCache = new TreeMap<>();

		for (String attemptID : students.keySet()) {
			AssignmentAttempt attempt = students.get(attemptID);

			if (attempt.size() == 0) continue;
			if (assignment.graded && attempt.grade == null) {
				System.err.println("No grade for: " + attemptID);
			}

			LoadedAttempt nodes = new LoadedAttempt(attempt.hasIDs, attempt.grade);
			for (AttemptAction row : attempt) {
				Node node = SimpleNodeBuilder.toTree(row.snapshot, true);
				nodes.add(node);
			}

			nodeMapCache.put(attemptID, nodes);
		}

		return nodeMapCache;
	}

	public static class LoadedAttempt extends ArrayList<Node> {
		private static final long serialVersionUID = 1L;

		public final boolean hasIDs;
		public final Grade grade;

		public LoadedAttempt(boolean hasIDs, Grade grade) {
			this.hasIDs = hasIDs;
			this.grade = grade;
		}
	}

	public static Kryo getKryo() {
		Kryo kryo = new Kryo();
		kryo.register(HintMapBuilder.class);
		kryo.register(StringHashable.class);
		kryo.register(Node.class);
		kryo.register(HintMap.class);
		kryo.register(VectorState.class);
		kryo.register(IndexedVectorState.class);
		kryo.register(VectorGraph.class);
		kryo.register(Graph.Vertex.class);
		kryo.register(Graph.Edge.class);
		return kryo;
	}
}
