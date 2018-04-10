package edu.isnap.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Fall2015;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.eval.policy.DirectEditPolicy;
import edu.isnap.eval.policy.HintFactoryPolicy;
import edu.isnap.eval.policy.HintPolicy;
import edu.isnap.eval.policy.StudentPolicy;
import edu.isnap.eval.util.PrintUpdater;
import edu.isnap.eval.util.Prune;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.SnapHintBuilder.LoadedAttempt;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;

public class SolveEval {

	private final static int MAX = 1000;
	private final static boolean PRUNE = true;

	private final static int SEED = 1234;
	private final static int MAX_HINTS = 1;

	private final static int ROUNDS = 1;

	private final static Random rand = new Random(SEED);

	public static void main(String[] args) throws IOException {

		Assignment assignment = Fall2015.GuessingGame1;

		policyGradeEval(assignment);
//		hintChainEval(assignment);
	}

	public static void policyGradeEval(Assignment assignment) throws FileNotFoundException, IOException {
		Snapshot solution = assignment.loadSolution();
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);

		eval(assignment, "solve" + MAX_HINTS, new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree) {
				HintMapBuilder builder0 = subtree.buildGenerator(student, 0);
				HintMapBuilder builder1 = subtree.buildGenerator(student, 1);
				Node studentLast = nodes.get(nodes.size() - 1);
				return new Score[] {
						new Score("Hint All", new HintFactoryPolicy(builder0)),
						new Score("Hint Exemplar", new HintFactoryPolicy(builder1)),
						new Score("Direct Ideal", solutionPolicy),
						new Score("Direct Student", new DirectEditPolicy(studentLast)),
						new Score("Student Next", new StudentPolicy(nodes))
				};
			}
		});
	}

	private static void eval(Assignment assignment, String test, ScoreConstructor constructor) throws IOException {

		SnapHintBuilder subtree = new SnapHintBuilder(assignment);

		File outFile = new File(assignment.analysisDir() + "/" + test + (PRUNE ? "-p" : "") + ".csv");
		outFile.getParentFile().mkdirs();
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(Score.headers()));



		for (int round = 0; round < ROUNDS; round++) {

			int max = MAX;

			Map<String, LoadedAttempt> nodeMap = subtree.nodeMap();
			for (String student : nodeMap.keySet()) {
				if (assignment.ignore(student)) continue;

				if (--max < 0) break;

				System.out.println(student);

				List<Node> nodes = nodeMap.get(student);
				if (PRUNE) nodes = Prune.removeSmallerScripts(nodes);

				Score[] scores = constructor.construct(student, nodes, subtree);

				AtomicInteger count = new AtomicInteger(0);
				int total = 0;

				HashMap<String, Integer> solveSteps = new HashMap<>();
				for (int i = 0; i < nodes.size(); i++) {
					Node node = nodes.get(i);

					for (Grader grader : AutoGrader.graders) {
						String name = grader.name();
						if (!solveSteps.containsKey(name) && grader.pass(node)) {
							solveSteps.put(name, i);
						}
					}

					for (Score score : scores) {
	//					System.out.println(score.name);
	//					score.update(node, i, rand.nextInt());
						score.updateAsync(node, i, rand.nextInt(), count);
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
					score.writeRow(printer, student, solveSteps, round);
	//				score.print();
				}
			}
		}

		printer.close();
	}

	public interface ScoreConstructor {
		Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree);
	}

	protected static class Score {
		private final HashMap<String, Integer> solveSteps = new HashMap<>();
		private final HashMap<String, Integer> solveStepsSelect = new HashMap<>();
		private int hints, selectHints, actions;

		public final HintPolicy policy;
		public final String name;

		public Score(String name, HintPolicy policy) {
			this.name = name;
			this.policy = policy;
		}

		public static String[] headers() {
			List<String> headers = new LinkedList<>();
			headers.add("policy"); headers.add("student"); headers.add("round"); headers.add("limited"); headers.add("hints"); headers.add("actions");
			for (int i = 0; i < AutoGrader.graders.length; i++) headers.add("test" + i);
			return headers.toArray(new String[headers.size()]);
		}

		public void writeRow(CSVPrinter printer, String student, HashMap<String, Integer> studentSolveSteps, int round) throws IOException {
			int extraCols = 6;
			for (int i = 0; i < 2; i++) {
				Object[] row = new Object[AutoGrader.graders.length + extraCols];
				row[0] = name; row[1] = student; row[2] = round; row[3] = i == 0 ? "FALSE" : "TRUE";
				row[4] = (i == 0 ? hints : selectHints); row[5] = actions;
				for (int j = 0; j < AutoGrader.graders.length; j++) {
					Integer solveStep = (i == 0 ? solveSteps : solveStepsSelect).get(AutoGrader.graders[j].name());
					Integer studentSolveStep = studentSolveSteps.get(AutoGrader.graders[j].name());
					if (studentSolveStep == null) {
						row[j + extraCols] = solveStep == null ? 0 : "NA";
					} else {
						if (solveStep == null) solveStep = studentSolveStep;
						row[j + extraCols] = studentSolveStep - solveStep;
					}
				}
				printer.printRecord(row);
			}
			printer.flush();
		}

		public void updateAsync(final Node node, int step, int seed, AtomicInteger count) {
			count.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					update(node, step, seed);
					count.decrementAndGet();
				}
			}).start();
		}

		public void update(Node node, int step, int seed) {
			Set<Node> steps = policy.nextSteps(node);
			int nSelect = Math.min(steps.size(), MAX_HINTS);

			synchronized (this) {
				hints += steps.size();
				selectHints += nSelect;
				actions++;
			}

			if (steps.size() == 0) return;

			Random rand = new Random(seed);
			IntStream selectStream = rand.ints(0, steps.size()).distinct().limit(nSelect);
			HashSet<Integer> select = new HashSet<>();
			selectStream.forEach(select::add);

			for (Grader grader : AutoGrader.graders) {
				String name = grader.name();
				boolean pass = false;
				boolean passSelect = false;
				int i = 0;
				for (Node next : steps) {
					if (grader.pass(next)) {
						pass = true;
						if (select.contains(i)) passSelect = true;
					}
					i++;
				}
				update(solveSteps, step, name, pass);
				update(solveStepsSelect, step, name, passSelect);
			}
		}

		private void update(HashMap<String, Integer> solveSteps, int step, String name, boolean pass) {
			if (pass) {
				synchronized (solveSteps) {
					Integer s = solveSteps.get(name);
					if (s == null || s > step) s = step;
					solveSteps.put(name, s);
				}
			}
		}
	}
}
