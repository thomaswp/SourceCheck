package edu.isnap.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import distance.RTED_InfoTree_Opt;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Grade;
import edu.isnap.datasets.Fall2015;
import edu.isnap.eval.policy.DirectEditPolicy;
import edu.isnap.eval.policy.HintFactoryPolicy;
import edu.isnap.eval.policy.HintPolicy;
import edu.isnap.eval.policy.StudentPolicy;
import edu.isnap.eval.util.PrintUpdater;
import edu.isnap.eval.util.Prune;
import edu.isnap.hint.HintMapBuilder;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.SnapHintBuilder.LoadedAttempt;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.elements.Snapshot;
import util.LblTree;

public class PredictionEval {

	private final static int MAX = 1000, LOOK_AHEAD = 5, STEP = 5;
	private final static boolean PRUNE = true;

	public static void main(String[] args) throws IOException {

		Assignment assignment = Fall2015.GuessingGame1;

//		predictionEval(assignment);
		distanceEval(assignment);
	}

	public static void predictionEval(Assignment assignment) throws FileNotFoundException, IOException {
		Snapshot solution = assignment.loadSolution();
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);

		eval(assignment, "prediction", new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree) {
				HintMapBuilder builder0 = subtree.buildGenerator(student, 0);
				HintMapBuilder builder1 = subtree.buildGenerator(student, 1);
				return new Score[] {
						new BinaryScore("Hint All", new HintFactoryPolicy(builder0)),
						new BinaryScore("Hint Exemplar", new HintFactoryPolicy(builder1)),
						new BinaryScore("Direct Ideal", solutionPolicy),
						new BinaryScore("Direct Student", new DirectEditPolicy(nodes.get(nodes.size() - 1))),
				};
			}

			@Override
			public String[] headers() {
				return BinaryScore.headers();
			}
		});
	}

	public static void distanceEval(Assignment assignment) throws FileNotFoundException, IOException {
		Snapshot solution = assignment.loadSolution();
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);

		eval(assignment, "distance", new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree) {
				HintMapBuilder builder0 = subtree.buildGenerator(student, 0);
				HintMapBuilder builder1 = subtree.buildGenerator(student, 1);
				Node studentLast = nodes.get(nodes.size() - 1);
				return new Score[] {
						new DistanceScore("Hint All", new HintFactoryPolicy(builder0)),
						new DistanceScore("Hint Exemplar", new HintFactoryPolicy(builder1)),
						new DistanceScore("Direct Ideal", solutionPolicy),
						new DistanceScore("Direct Student", new DirectEditPolicy(studentLast)),
						new DistanceScore("Student Next", new StudentPolicy(nodes)),
				};
			}

			@Override
			public String[] headers() {
				return DistanceScore.headers();
			}
		});
	}

	private static void eval(Assignment assignment, String test, ScoreConstructor constructor) throws IOException {

		SnapHintBuilder subtree = new SnapHintBuilder(assignment);

		File outFile = new File(assignment.analysisDir() + "/" + test + (PRUNE ? "-p" : "") + ".csv");
		outFile.getParentFile().mkdirs();
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(constructor.headers()));

		int max = MAX;

		Map<String, LoadedAttempt> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (assignment.ignore(student)) continue;

			Grade grade = nodeMap.get(student).grade;
			if (grade == null) continue;

			if (--max < 0) break;

			System.out.println(student);

			List<Node> nodes = nodeMap.get(student);
			if (PRUNE) nodes = Prune.removeSmallerScripts(nodes);

			Score[] scores = constructor.construct(student, nodes, subtree);

			AtomicInteger count = new AtomicInteger(0);
			int total = 0;

			for (int i = 0; i < nodes.size() - 1; i++) {
				Node node = nodes.get(i);
				Node next = nodes.get(i + 1);
				if (node.equals(next)) continue;

				for (Score score : scores) {
//					score.update(nodes, i);
					score.updateAsync(nodes, i, count);
					total++;
				}
			}

			PrintUpdater updater = new PrintUpdater(40);
			while (count.get() > 0) {
				try {
					Thread.sleep(100);
					updater.updateTo((total - (double)count.get()) / total);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			System.out.println();

			for (int i = 0; i < scores.length; i++) {
				Score score = scores[i];
				score.writeRow(printer, student, grade.average());
			}
		}

		printer.close();
	}

	public interface ScoreConstructor {
		String[] headers();
		Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree);
	}

	protected static class DistanceScore extends Score {

		private double nodeStepDis, nodeFinalDis, hintStepDis, hintFinalDis;
		private double nodeStepDisN, nodeFinalDisN, hintStepDisN, hintFinalDisN;
		private int closerStep, closerFinal, fartherStep, fartherFinal;
		private int deletions;
		private int totalHints, totalActions;

		private final static HashMap<String, Double> cachedDistances = new HashMap<>();

		public DistanceScore(String name, HintPolicy policy) {
			super(name, policy);
		}

		public static String[] headers() {
			return new String[] {
				"student", "policy", "grade", "normalized", "target",
				"nodeDis", "hintDis", "closer", "farther",
				"deletions", "totalHints", "totalAction"
			};
		}

		@Override
		public void writeRow(CSVPrinter printer, String student, double grade) throws IOException {
			printer.printRecord(student, name, grade, "FALSE", "step",
					nodeStepDis, hintStepDis, closerStep, fartherStep,
					deletions, totalHints, totalActions);
			printer.printRecord(student, name, grade, "TRUE", "step",
					nodeStepDisN, hintStepDisN, closerStep, fartherStep,
					deletions, totalHints, totalActions);
			printer.printRecord(student, name, grade, "FALSE", "final",
					nodeFinalDis, hintFinalDis, closerFinal, fartherFinal,
					deletions, totalHints, totalActions);
			printer.printRecord(student, name, grade, "TRUE", "final",
					nodeFinalDisN, hintFinalDisN, closerFinal, fartherFinal,
					deletions, totalHints, totalActions);
		}

		private double dis(RTED_InfoTree_Opt opt, Node n1, Node n2, LblTree t1, LblTree t2) {
			String key = n1 + "+" + n2;
			synchronized (cachedDistances) {
				if (cachedDistances.containsKey(key)) {
					return cachedDistances.get(key);
				}
			}
			if (t1 == null) t1 = n1.toTree();
			if (t2 == null) t2 = n2.toTree();
			double dis = opt.nonNormalizedTreeDist(t1, t2);
			synchronized (cachedDistances) {
				cachedDistances.put(key, dis);
			}
			return dis;
		}

		@Override
		public void update(List<Node> nodes, int index) {
			Set<Node> hints = policy.nextSteps(nodes.get(index));
			int nHints = hints.size();
			if (nHints == 0) return;

			RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
			RTED_InfoTree_Opt optDel = new RTED_InfoTree_Opt(1, 0, 1);

			Node now = nodes.get(index);
			int end = nodes.size() - 1;
			Node fin = nodes.get(end);
			Node step = nodes.get(Math.min(end, index + STEP));

			LblTree nowTree = now.toTree();
			LblTree finTree = fin.toTree();
			LblTree stepTree = step.toTree();

			int nowSize = now.treeSize();

			double nodeStepDis = dis(opt, now, step, nowTree, stepTree);
			double nodeStepDisN = nodeStepDis / nowSize;
			double nodeFinDis = dis(opt, now, fin, nowTree, finTree);
			double nodeFinDisN = nodeFinDis / nowSize;

			double hintStepDis = 0;
			double hintStepDisN = 0;
			double hintFinDis = 0;
			double hintFinDisN = 0;

			int closerStep = 0, closerFinal = 0, fartherStep = 0, fartherFinal = 0;

//			System.out.println("Fin: " + fin);
//			System.out.println("Node " + nodeFinDis + ": " + now);

			int nDeletions = 0;

			for (Node hint : hints) {
				LblTree hintTree = hint.toTree();

				double hintStepDis1 = dis(opt, hint, step, hintTree, stepTree);
				double hintStepDisN1 = hintStepDis1 / nowSize;
				double hintFinDis1 = dis(opt, hint, fin, hintTree, finTree);
				double hintFinDisN1 = hintFinDis1 / nowSize;

//				System.out.println("Hint " + hintFinDis1 + ": " + hint);

				hintStepDis += hintStepDis1;
				hintStepDisN += hintStepDisN1;
				hintFinDis += hintFinDis1;
				hintFinDisN += hintFinDisN1;

				if (hintStepDis1 < nodeStepDis) closerStep++;
				else if (hintStepDis1 > nodeStepDis) fartherStep++;
				if (hintFinDis1 < nodeFinDis) closerFinal++;
				else if (hintFinDis1 > nodeFinDis) fartherFinal++;

				double deletions = optDel.nonNormalizedTreeDist(nowTree, hintTree);
				if (deletions > 0) nDeletions++;
			}

			synchronized (this) {
				this.nodeStepDis += nodeStepDis;
				this.nodeStepDisN += nodeStepDisN;
				this.nodeFinalDis += nodeFinDis;
				this.nodeFinalDisN += nodeFinDisN;

				this.hintStepDis += hintStepDis / nHints;
				this.hintStepDisN += hintStepDisN / nHints;
				this.hintFinalDis += hintFinDis / nHints;
				this.hintFinalDisN += hintFinDisN / nHints;

				this.closerStep += closerStep;
				this.fartherStep += fartherStep;
				this.closerFinal += closerFinal;
				this.fartherFinal += fartherFinal;

				this.totalActions++;
				this.deletions += nDeletions;
				this.totalHints += nHints;
			}
		}
	}

	protected static class BinaryScore extends Score {

		public BinaryScore(String name, HintPolicy policy) {
			super(name, policy);
		}

		private int predicted;
		private int totalHints;
		private int totalActions;

		public static String[] headers() {
			return new String[] {
				"policy", "student", "grade", "predicted", "hints", "actions"
			};
		}

		@Override
		public void writeRow(CSVPrinter printer, String student, double grade) throws IOException {
			printer.printRecord(name, student, grade, predicted, totalHints, totalActions);
			printer.flush();
		}

		@Override
		public void update(List<Node> nodes, int index) {
			Set<Node> steps = policy.nextSteps(nodes.get(index));

			int pred = 0;
			int hints = steps.size();

			int end = Math.min(index + LOOK_AHEAD + 1, nodes.size());
			for (int i = index + 1; i < end; i++) {
				Node node = nodes.get(i);
				if (steps.contains(node)) {
					steps.remove(node);
					pred++;
					break;
				}
			}

			synchronized (this) {
				totalHints += hints;
				totalActions++;
				predicted += pred;
			}
		}
	}

	protected static abstract class Score {
		public final HintPolicy policy;
		public final String name;

		public Score(String name, HintPolicy policy) {
			this.name = name;
			this.policy = policy;
		}

		public abstract void update(List<Node> nodes, int index);
		public abstract void writeRow(CSVPrinter printer, String student, double grade) throws IOException;

		public void updateAsync(final List<Node> nodes, final int index, AtomicInteger count) {
			count.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					update(nodes, index);
					count.decrementAndGet();
				}
			}).start();
		}


	}
}
