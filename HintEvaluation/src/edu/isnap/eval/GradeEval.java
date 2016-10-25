package edu.isnap.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintGenerator;
import edu.isnap.ctd.util.Tuple;
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
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;

public class GradeEval {
	
	private final static int MAX = 1000;
	private final static boolean PRUNE = true;
	
	public static void main(String[] args) throws IOException {
		Assignment assignment = Fall2015.GuessingGame1;
		
		policyGradeEval(assignment);
//		hintChainEval(assignment);
//		pruneTest(assignment);
	}

	public static void hintChainEval(Assignment assignment) throws FileNotFoundException, IOException {
			
		eval(assignment, "chain", new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree) {
				HintGenerator builder0 = subtree.buildGenerator(student, 0);
				HintGenerator builder1 = subtree.buildGenerator(student, 1);
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
	
	public static void policyGradeEval(Assignment assignment) throws FileNotFoundException, IOException {
		Snapshot solution = assignment.loadSolution();
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);
				
		eval(assignment, "grade", new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree) {
				HintGenerator builder0 = subtree.buildGenerator(student, 0);
				HintGenerator builder1 = subtree.buildGenerator(student, 1);
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
	
	@SuppressWarnings("unused")
	private static void pruneTest(Assignment assignment) throws IOException {
		
		SnapHintBuilder subtree = new SnapHintBuilder(assignment);
		
		int max = 1;
		
		Map<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (assignment.ignore(student)) continue;
			
			if (--max < 0) break;
			
			System.out.println(student);
			
			List<Node> nodes = nodeMap.get(student);
			List<Node> nodesPruned = Prune.removeSmallerScripts(nodes);
			
			DirectEditPolicy policy = new DirectEditPolicy(nodes.get(nodes.size() - 1));
			DirectEditPolicy policyPruned = new DirectEditPolicy(nodesPruned.get(nodesPruned.size() - 1));
			
			for (int i = 0; i < nodes.size(); i++) {
				Node node = nodes.get(i);
				Node nodePruned = nodesPruned.get(i);
				
				Grader grader = AutoGrader.graders[3];
				boolean grade = grader.pass(node);
				boolean gradePruned = grader.pass(nodePruned);
				
				if (grade && gradePruned) {					
					Set<Node> nextStepsPruned = policyPruned.nextSteps(nodePruned);
					
					boolean failed = false;
					for (Node n : nextStepsPruned) {
						if (!grader.pass(n)) {
							failed = true;
							break;
						}
					}
					if (failed) continue;
					
					Set<Node> nextSteps = policy.nextSteps(node);
					for (Node n : nextSteps) {
						if (!grader.pass(n)) {
							System.out.println("Node");
							System.out.println(node.prettyPrint());
							System.out.println("Node Pruned");
							System.out.println(nodePruned.prettyPrint());
							System.out.println("Node Hint");
							System.out.println(n.prettyPrint());
							System.out.println("-----------------------");
							break;
						}
					}					
				}
			}
		}
	}
	
	private static void eval(Assignment assignment, String test, ScoreConstructor constructor) throws IOException {
		
		SnapHintBuilder subtree = new SnapHintBuilder(assignment);
		
		File outFile = new File(assignment.analysisDir() + "/" + test + (PRUNE ? "-p" : "") + ".csv");
		outFile.getParentFile().mkdirs();
		List<String> headers = new LinkedList<>();
		headers.add("policy"); headers.add("student"); headers.add("action"); headers.add("total");
		for (int i = 0; i < AutoGrader.graders.length; i++) headers.add("test" + i);
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(headers.toArray(new String[headers.size()])));
		
		int max = MAX;
		
		Map<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (assignment.ignore(student)) continue;
			
			if (--max < 0) break;
			
			System.out.println(student);
			
			List<Node> nodes = nodeMap.get(student);
			if (PRUNE) nodes = Prune.removeSmallerScripts(nodes);
			
			Score[] scores = constructor.construct(student, nodes, subtree);
			
			AtomicInteger count = new AtomicInteger(0);
			int total = 0;
			
			for (int i = 0; i < nodes.size(); i++) {
				Node node = nodes.get(i);
				HashMap<String,Boolean> grade = AutoGrader.grade(node);
				
				for (Score score : scores) {
//					System.out.println(score.name);
//					score.update(node, grade);
					score.updateAsync(node, grade, count);
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
				score.writeRow(printer, student);
//				score.print();
			}
		}
		
		printer.close();
	}
	
	public interface ScoreConstructor {
		Score[] construct(String student, List<Node> nodes, SnapHintBuilder subtree);
	}
	
	protected static class Score {
		public final HashMap<String, Tuple<Integer, Integer>> outcomes = 
				new HashMap<String, Tuple<Integer, Integer>>();
		public int totalSteps;
		
		public final HintPolicy policy;
		public final String name;
		
		// TODO: Remove duplicates?
//		private Set<Tuple<VectorHint, Boolean>> seen = new HashSet<Tuple<VectorHint, Boolean>>();
		
		public Score(String name, HintPolicy policy) {
			this.name = name;
			this.policy = policy;
			for (Grader grader : AutoGrader.graders) {
				outcomes.put(grader.name(), new Tuple<Integer, Integer>(0, 0));
			}
		}
		
		public void writeRow(CSVPrinter printer, String student) throws IOException {
			int extraCols = 4;
			for (int i = 0; i < 2; i++) {
				Object[] row = new Object[AutoGrader.graders.length + extraCols];
				row[0] = name; row[1] = student; row[2] = i == 0 ? "do" : "undo"; row[3] = totalSteps;
				for (int j = 0; j < AutoGrader.graders.length; j++) {
					Tuple<Integer, Integer> outcome = outcomes.get(AutoGrader.graders[j].name());
					row[j + extraCols] = i == 0 ? outcome.y : outcome.x;
				}
				printer.printRecord(row);
			}
			printer.flush();
		}

		public void print() {
			System.out.println(name + ":");
			for (int i = 0; i < AutoGrader.graders.length; i++) {
				String obj = AutoGrader.graders[i].name();
				Tuple<Integer, Integer> outcome = outcomes.get(obj);
				System.out.println(obj + ": " + outcome);
			}
			System.out.println("Total: " + totalSteps);
			System.out.println();
		}

		public void add(Score score) {
			for (String key : score.outcomes.keySet()) {
				Tuple<Integer, Integer> outcome = outcomes.get(key);
				Tuple<Integer, Integer> otherOutcome = score.outcomes.get(key);
				outcome.x += otherOutcome.x;
				outcome.y += otherOutcome.y;
			}
			totalSteps += score.totalSteps;
		}
		
		public void updateAsync(final Node node, final HashMap<String,Boolean> grade, AtomicInteger count) {
			count.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					update(node, grade);
					count.decrementAndGet();
				}
			}).start();
		}
		
		public void update(Node node, HashMap<String,Boolean> grade) {
			Set<Node> steps = policy.nextSteps(node);
			
			for (Node next : steps) {
//				System.out.println(next);
				HashMap<String,Boolean> nextGrade = AutoGrader.grade(next);
				for (String obj : nextGrade.keySet()) {
					boolean a = grade.get(obj);
					boolean b = nextGrade.get(obj);
					
					Tuple<Integer, Integer> outcome = outcomes.get(obj);
					if (a == true && b == false) {
//						Tuple<VectorHint, Boolean> record = new Tuple<VectorHint, Boolean>(vHint, false);
//						if (seen.add(record)) {
							synchronized (outcome) {
								outcome.x++;
							}
//						}
					} else if (a == false && b == true) {
//						Tuple<VectorHint, Boolean> record = new Tuple<VectorHint, Boolean>(vHint, true);
//						if (seen.add(record)) {
							synchronized (outcome) {
								outcome.y++;
							}
//						}
					}
				}
				synchronized (this) {
					totalSteps++;
				}
			}
		}
	}
}
