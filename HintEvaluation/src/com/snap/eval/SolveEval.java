package com.snap.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.snap.data.Snapshot;
import com.snap.eval.AutoGrader.Grader;
import com.snap.eval.policy.DirectEditPolicy;
import com.snap.eval.policy.HintFactoryPolicy;
import com.snap.eval.policy.HintPolicy;
import com.snap.eval.policy.StudentPolicy;
import com.snap.eval.util.PrintUpdater;
import com.snap.eval.util.Prune;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;

public class SolveEval {
	
	private final static int SKIP = 1;
	private final static int MAX = 100;
	private final static boolean PRUNE = true;
	
	private final static int SEED = 1234;
	private final static int MAX_HINTS = 1;
		
	private final static int ROUNDS = 1;
	
	private final static Random rand = new Random(SEED);
	
	public static void main(String[] args) throws IOException {
				
		String dir = "../data/csc200/fall2015";
		String assignment = "guess1Lab";
		
		policyGradeEval(dir, assignment);
//		hintChainEval(dir, assignment);
	}

	public static void hintChainEval(String dir, String assignment) throws FileNotFoundException, IOException {
			
		eval(dir, assignment, "solve-chain" + MAX_HINTS, new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapSubtree subtree) {
				SubtreeBuilder builder0 = subtree.buildGraph(student, 0);
				SubtreeBuilder builder1 = subtree.buildGraph(student, 1);
				return new Score[] {
						new Score("All 2", new HintFactoryPolicy(builder0, 2)),
						new Score("Exemplar 2", new HintFactoryPolicy(builder1, 2)),
						new Score("All 3", new HintFactoryPolicy(builder0, 3)),
						new Score("Exemplar 3", new HintFactoryPolicy(builder1, 3)),
						new Score("All End", new HintFactoryPolicy(builder0, 25)),
						new Score("Exemplar End", new HintFactoryPolicy(builder1, 25)),
				};
			}
		});
	}
	
	public static void policyGradeEval(String dir, String assignment) throws FileNotFoundException, IOException {
		Snapshot solution = Snapshot.parse(new File(dir + "/solutions/", assignment + ".xml"));
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);
				
		eval(dir, assignment, "solve" + MAX_HINTS, new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapSubtree subtree) {
				SubtreeBuilder builder0 = subtree.buildGraph(student, 0);
				SubtreeBuilder builder1 = subtree.buildGraph(student, 1);
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
	
	private static void eval(String dir, String assignment, String test, ScoreConstructor constructor) throws IOException {
		
		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree(dir, assignment, maxTime, new HintFactoryMap());
		
		File outFile = new File(dir + "/anlysis/" + assignment + "/" + test + (PRUNE ? "-p" : "") + ".csv");
		outFile.getParentFile().mkdirs();
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(Score.headers()));
		

		
		for (int round = 0; round < ROUNDS; round++) {
		
			int skip = SKIP;
			int max = MAX;
			
			HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
			for (String student : nodeMap.keySet()) {
				if (skip-- > 0) {
					continue;
				}
				
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
						updater.update((total - (double)count.get()) / total);
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
		Score[] construct(String student, List<Node> nodes, SnapSubtree subtree);
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
