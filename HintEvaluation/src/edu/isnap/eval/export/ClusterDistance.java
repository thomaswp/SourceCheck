package edu.isnap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import distance.RTED_InfoTree_Opt;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AssignmentAttempt.ActionRows;
import edu.isnap.datasets.csc200.Fall2016;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.Alignment;
import edu.isnap.hint.util.KMedoids.DistanceMeasure;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.Store.Mode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.util.map.DoubleMap;

public class ClusterDistance {

	public interface NodeDistanceMeasure extends DistanceMeasure<Node> {
		String name();
	}

	public final static NodeDistanceMeasure RTEDDistanceMeasure = new NodeDistanceMeasure() {
		private final ThreadLocal<RTED_InfoTree_Opt> opt = new ThreadLocal<RTED_InfoTree_Opt>() {
			@Override
			protected RTED_InfoTree_Opt initialValue() {
				return new RTED_InfoTree_Opt(1, 1, 1);
			};
		};

		@Override
		public double measure(Node a, Node b) {
			return opt.get().nonNormalizedTreeDist(a.toTree(), b.toTree());
		}

		@Override
		public String name() {
			return "RTED";
		}
	};

	public final static NodeDistanceMeasure DFSAlignmentMeasure = new NodeDistanceMeasure() {
		@Override
		public double measure(Node a, Node b) {
			return Alignment.alignCost(a.depthFirstIteration(), b.depthFirstIteration());
		}

		@Override
		public String name() {
			return "SED_DFS";
		}
	};

	public final static NodeDistanceMeasure DFSNormAlignmentMeasure =
			new NodeDistanceMeasure() {
		@Override
		public double measure(Node a, Node b) {
			return Alignment.normAlignCost(a.depthFirstIteration(), b.depthFirstIteration(),
					1, 1, 1);
		}

		@Override
		public String name() {
			return "SED_BFS";
		}
	};

	public final static NodeDistanceMeasure NodeCountMeasure = new NodeDistanceMeasure() {
		@Override
		public double measure(Node a, Node b) {
			String[] aDF = a.depthFirstIteration();
			Arrays.sort(aDF);
			String[] bDF = b.depthFirstIteration();
			Arrays.sort(bDF);
			return Alignment.alignCost(aDF, bDF, 1, 1, 100);
		}

		@Override
		public String name() {
			return "BoW";
		}
	};

	public final static NodeDistanceMeasure EditCountMeasure = new NodeDistanceMeasure() {
		@Override
		public double measure(Node a, Node b) {
			List<Node> la = Collections.singletonList(a);
			List<Node> lb = Collections.singletonList(b);
			HintConfig config = new SnapHintConfig();
			config.preprocessSolutions = false;
			int editsA = new HintHighlighter(la, config).highlight(b).size();
			int editsB = new HintHighlighter(lb, config).highlight(a).size();
			return (editsA + editsB) / 2.0;
		}

		@Override
		public String name() {
			return "SourceCheck_EditCount";
		}
	};

	public final static NodeDistanceMeasure SourceCheckMeasure = new NodeDistanceMeasure() {
		@Override
		public double measure(Node a, Node b) {
			HintConfig config = new SnapHintConfig();
			double distance = new NodeAlignment(a, b, config)
					.calculateMapping(HintHighlighter.getDistanceMeasure(config)).cost();
			return distance / a.treeSize();
		}

		@Override
		public String name() {
			return "SourceCheck";
		};
	};

	public final static List<NodeDistanceMeasure> DistanceMeasures = new ArrayList<>(
			Arrays.asList(
				RTEDDistanceMeasure, DFSAlignmentMeasure, DFSNormAlignmentMeasure, NodeCountMeasure,
				EditCountMeasure, SourceCheckMeasure
			));

	public static void main(String[] args) throws FileNotFoundException, IOException {
		List<Assignment> assignments = new LinkedList<>();
		assignments.add(Fall2016.GuessingGame2);
		write(Fall2016.GuessingGame2.analysisDir(), assignments, DFSAlignmentMeasure, 120, 1);
	}

	private static void write(String path, List<Assignment> assignments,
			NodeDistanceMeasure dm, int snapshotIntervalSeconds, int skipRatio)
					throws FileNotFoundException, IOException {

		Map<String, Node> nodes = new TreeMap<>();

		boolean fullPath = snapshotIntervalSeconds > 0;

		File file = new File(path, "snapshots-" + (fullPath ? "path" : "submitted") + ".csv");
		file.getParentFile().mkdirs();
		CSVPrinter printer = new CSVPrinter(new PrintStream(file), CSVFormat.DEFAULT.withHeader(
				"id", "dataset", "assignment", "attempt", "i", "start", "submitted", "ast",
				"hints"));

		int k = 0;
		for (Assignment assignment : assignments) {
			Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
			String datasetName = assignment.dataset.getName();

			for (AssignmentAttempt attempt : attempts.values()) {
				if (!attempt.isLikelySubmitted()) continue;
				if (k++ % skipRatio != 0) continue;

				int hints = 0;

				int size = attempt.size();
				ActionRows rows = attempt.rows;
				long lastActionTime = 0;
				int count = 0;
				Node lastNode = null;
				for (int i = 0; i < size; i++) {
					AttemptAction action = rows.get(i);

					if (action.message.equals(AttemptAction.HINT_DIALOG_DESTROY)) {
						hints++;
					}

					if (action.snapshot == null) continue;
					if (!fullPath && action.snapshot != attempt.submittedSnapshot) continue;

					long timestamp = action.timestamp.getTime() / 1000;
					if (i < size - 1 && timestamp < lastActionTime + snapshotIntervalSeconds) {
						continue;
					}
					Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
					if (i < size - 1 && node.equals(lastNode)) continue;
					lastActionTime = timestamp;
					lastNode = node;
					String id = attempt.id + "-" + action.id;
					if (nodes.put(id, node) != null) {
						printer.close();
						throw new RuntimeException("Two snapshots with ID: " + id);
					}

					printer.printRecord(id, datasetName, assignment.name, attempt.id, count++,
							i == 0, action.snapshot == attempt.submittedSnapshot, node.toString(),
							hints);
				}
			}
		}
		printer.close();

		int n = nodes.size();
		System.out.println("n = " + n);
		double[][] distances = new double[n][n];
		Node[] nodeArray = nodes.values().toArray(new Node[n]);
		DoubleMap<Node, Node, Double> cache = new DoubleMap<>();
		int tick = n * n / 50;

		ExecutorService executor = Executors.newFixedThreadPool(4);

		System.out.println(new String(new char[51]).replace("\0", "-"));
		for (int i = 0; i < n; i++) {
			final int fi = i;
			executor.submit(new Runnable() {
				@Override
				public void run() {
					Node a = nodeArray[fi];
					for (int j = 0; j < n; j++) {
						Node b = nodeArray[j];
						Double dis = cache.get(a, b);
						if (dis == null) {
							cache.put(a, b, dis = dm.measure(a, b));
						}
						distances[fi][j] = dis;
						if ((fi * n + j) % tick == 0) System.out.print("+");
					}
				}
			});
		}
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println();

		String[] header = nodes.keySet().toArray(new String[n]);
		file = new File(path, "matrix-" + (fullPath ? "path" : "submitted") + ".csv");
		file.getParentFile().mkdirs();
		printer = new CSVPrinter(new PrintStream(file), CSVFormat.DEFAULT.withHeader(header));
		for (double[] row : distances) {
			for (double d : row) {
				printer.print(d);
			}
			printer.println();
		}
		printer.close();
	}
}
