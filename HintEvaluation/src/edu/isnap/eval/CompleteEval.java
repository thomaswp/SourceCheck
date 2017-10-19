package edu.isnap.eval;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import distance.RTED_InfoTree_Opt;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Fall2015;
import edu.isnap.eval.policy.DirectEditPolicy;
import edu.isnap.eval.policy.HintFactoryPolicy;
import edu.isnap.eval.policy.HintPolicy;
import edu.isnap.eval.util.PrintUpdater;
import edu.isnap.eval.util.Prune;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.SnapHintBuilder.LoadedAttempt;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;

public class CompleteEval {

	private final static int MAX = 1000, MAX_STEPS = 250, SLICES = 50;
	private final static boolean PRUNE = true;

	public static void main(String[] args) throws IOException {
		Assignment assignment = Fall2015.GuessingGame1;
		eval(assignment);
//		test(assignment);
	}

	private static void eval(Assignment assignment) throws IOException {

		SnapHintBuilder subtree = new SnapHintBuilder(assignment);

		Snapshot solution = assignment.loadSolution();
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);

		File outFile = new File(assignment.analysisDir() + "/complete" + (PRUNE ? "-p" : "") + ".csv");
		outFile.getParentFile().mkdirs();
		List<String> headers = new LinkedList<>();
		headers.add("policy"); headers.add("student"); headers.add("slice"); headers.add("studentSteps"); headers.add("hash"); headers.add("steps"); headers.add("deletions");  headers.add("firstHints");
		for (int i = 0; i < AutoGrader.graders.length; i++) headers.add("test" + i);
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(headers.toArray(new String[headers.size()])));

		int max = MAX;

		String[] names = new String[] { "Hint All", "Hint Exemplar", "Direct Ideal", "Direct Student" };

		Map<String, LoadedAttempt> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (assignment.ignore(student)) continue;

			if (--max < 0) break;

			System.out.println(student);

			List<Node> nodes = nodeMap.get(student);
			if (PRUNE) nodes = Prune.removeSmallerScripts(nodes);

			AtomicInteger count = new AtomicInteger(0);
			int total = 0;

			HintPolicy[] policies = new HintPolicy[] {
					new HintFactoryPolicy(subtree.buildGenerator(student, 0)),
					new HintFactoryPolicy(subtree.buildGenerator(student, 1)),
					solutionPolicy,
					new DirectEditPolicy(nodes.get(nodes.size() - 1)),
			};

			List<Completion> completions = new ArrayList<>();
			for (int i = 0; i < policies.length; i++) {

				for (int slice = 0; slice < SLICES; slice++) {
					int index = nodes.size() * slice / SLICES;
					Node node = nodes.get(index);
					Completion completion = new Completion(node, slice, nodes.size() - index - 1, policies[i], names[i]);
					completions.add(completion);
//					completion.calculate();
					completion.calculateAsync(count);
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

			for (Completion completion : completions) {
				completion.writeCSV(printer, student);
			}
		}

		printer.close();
	}

	@SuppressWarnings("unused")
	private static void test(Assignment assignment) throws IOException {

		SnapHintBuilder subtree = new SnapHintBuilder(assignment);

		Snapshot solution = assignment.loadSolution();
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);

		String[] names = new String[] { "Hint All", "Hint Exemplar", "Direct Ideal", "Direct Student" };

		Map<String, LoadedAttempt> nodeMap = subtree.nodeMap();

		int max = 2;

		for (String student : nodeMap.keySet()) {
			if (assignment.ignore(student)) continue;

			if (--max < 0) break;

			System.out.println(student);

			List<Node> nodes = nodeMap.get(student);

			HintPolicy[] policies = new HintPolicy[] {
					new HintFactoryPolicy(subtree.buildGenerator(student, 0)),
					new HintFactoryPolicy(subtree.buildGenerator(student, 1)),
					solutionPolicy,
					new DirectEditPolicy(nodes.get(nodes.size() - 1)),
			};

			int slice = (int) (Math.random() * SLICES);
			int index = nodes.size() * slice / SLICES;
			System.out.println("Slice: " + slice + " (" + index + ")");

			Node node = nodes.get(index);
			System.out.println(node.prettyPrint());
			node = Prune.removeSmallerScripts(node);
			System.out.println("Norm:\n" + node.prettyPrint());
			System.out.println("-------------");


			for (int i = 0; i < policies.length; i++) {
				System.out.println(names[i] + ":");
				Tuple<Node,Integer> s = policies[i].solution(node, MAX_STEPS);
				System.out.println("Steps: " + s.y);
				RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 0, 1);
				int deletions = (int)Math.round(opt.nonNormalizedTreeDist(node.toTree(), s.x.toTree()));
				System.out.println("Deletions: " + deletions);
				System.out.println();
				System.out.println(s.x.prettyPrint());
				System.out.println("-------------");

			}
		}
	}

	private static class Completion {

		private final Node startState;
		private final int slice, studentSteps;
		private final HintPolicy policy;
		private final String name;

		private Node endState;
		private HashMap<String, Boolean> grade;
		private int steps;
		private int deletions;
		private int firstHints;

		public Completion(Node startState, int slice, int studentSteps, HintPolicy policy, String name) {
			this.startState = startState;
			this.slice = slice;
			this.studentSteps = studentSteps;
			this.policy = policy;
			this.name = name;
		}

		public void calculateAsync(AtomicInteger count) {
			count.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					calculate();
					count.decrementAndGet();
				}
			}).start();
		}

		public void calculate() {
			Tuple<Node,Integer> solution = policy.solution(startState, MAX_STEPS);

			steps = solution.y;
			endState = solution.x;
			grade = AutoGrader.grade(endState);

			RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 0, 1);
			deletions = (int)Math.round(opt.nonNormalizedTreeDist(startState.toTree(), endState.toTree()));

			firstHints = policy.nextSteps(startState).size();
		}

		public void writeCSV(CSVPrinter printer, String student) throws IOException {
			int extraCols = 8;
			Object[] row = new Object[AutoGrader.graders.length + extraCols];
			row[0] = name; row[1] = student; row[2] = slice; row[3] = studentSteps;
			row[4] = "H" + endState.hashCode(); row[5] = steps; row[6] = deletions; row[7] = firstHints;

			for (int i = 0; i < AutoGrader.graders.length; i++) {
				row[i + extraCols] = String.valueOf(grade.get(AutoGrader.graders[i].name())).toUpperCase();
			}
			printer.printRecord(row);
			printer.flush();
		}

	}
}
