package edu.isnap.hint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import com.esotericsoftware.kryo.Kryo;

import edu.isnap.ctd.graph.Graph;
import edu.isnap.ctd.graph.vector.IndexedVectorState;
import edu.isnap.ctd.graph.vector.VectorGraph;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.CTDHintGenerator;
import edu.isnap.ctd.hint.CTDModel;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Grade;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.StringHashable;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store;
import edu.isnap.parser.Store.Mode;
import edu.isnap.sourcecheck.HintHighlighter;


/**
 * Class for constructing HintGenerators from Snap data and caching them for later use.
 */
public class SnapHintBuilder {

	public final Assignment assignment;
	private final HintConfig config;

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
		this(assignment, ConfigurableAssignment.getConfig(assignment));
	}

	public SnapHintBuilder(Assignment assignment, HintConfig config) {
		this.assignment = assignment;
		this.config = config;
	}

	/**
	 * Builds a HintGenerator with all attempts for this assignment.
	 * @param storeMode
	 * @return
	 */
	public HintData buildGenerator(Mode storeMode) {
		return buildGenerator(storeMode, 0);
	}

	/**
	 * Builds a HintGenerator with all attempts for this assignment that have at least the given
	 * minGrade.
	 * @param storeMode
	 * @param minGrade
	 * @return
	 */
	public HintData buildGenerator(Mode storeMode, final double minGrade) {
		String storePath = getStorePath(assignment, minGrade);
		HintData builder = Store.getCachedObject(getKryo(),
				storePath, HintData.class, storeMode,
				new Store.Loader<HintData>() {
			@Override
			public HintData load() {
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
		return new File(baseDir, String.format("%s-g%03d%s.hdata",
				assignmentName, Math.round(minGrade * 100),
				dataset == null ? "" : ("-" + dataset))).getAbsolutePath();
	}

	public static String getStorePath(String assignmentName, double minGrade, String dataset) {
		return String.format("%s-g%03d%s.hdata",
				assignmentName, Math.round(minGrade * 100),
				dataset == null ? "" : ("-" + dataset));
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
	public HintData buildGenerator(String testAttempt, double minGrade) {
		final HintData builder = new HintData(assignment.name, config, minGrade,
				HintHighlighter.DataConsumer, CTDHintGenerator.DataConsumer);
		for (String student : nodeMap().keySet()) {
			if (student.equals(testAttempt)) continue;

			final LoadedAttempt nodes = nodeMap().get(student);

			if (config.requireGrade && nodes.grade == null) continue;
			if (nodes.grade != null && nodes.grade.average() < minGrade) continue;

			builder.addTrace(nodes.id, nodes);
		}
		builder.finished();
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
			if (assignment.graded && attempt.researcherGrade == null) {
				System.err.println("No grade for: " + attemptID);
			}

			LoadedAttempt nodes = new LoadedAttempt(attempt.id, attempt.researcherGrade);
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

		public final String id;
		public final Grade grade;

		public LoadedAttempt(String id, Grade grade) {
			this.id = id;
			this.grade = grade;
		}
	}

	public static Kryo getKryo() {
		Kryo kryo = new Kryo();
		kryo.register(CTDModel.class);
		kryo.register(StringHashable.class);
		kryo.register(Node.class);
		kryo.register(HintMap.class);
		kryo.register(HintData.class);
		kryo.register(VectorState.class);
		kryo.register(IndexedVectorState.class);
		kryo.register(VectorGraph.class);
		kryo.register(Graph.Vertex.class);
		kryo.register(Graph.Edge.class);
		return kryo;
	}
}
