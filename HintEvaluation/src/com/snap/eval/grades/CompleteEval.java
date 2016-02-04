package com.snap.eval.grades;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.snap.eval.util.PrintUpdater;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.graph.subtree.SubtreeBuilder.Hint;

public class CompleteEval {
	
	private final static int SKIP = 1, MAX = 100, MAX_STEPS = 100, SLICES = 50;
	
	public static void main(String[] args) throws IOException {
		String dir = "../data/csc200/fall2015";
		String assignment = "guess1Lab";
		eval(dir, assignment);
	}
	
	private static void eval(String dir, String assignment) throws IOException {

		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree(dir, assignment, maxTime, new HintFactoryMap());

		File outFile = new File(dir + "/anlysis/" + assignment + "/complete.csv");
		outFile.getParentFile().mkdirs();
		List<String> headers = new LinkedList<>();
		headers.add("policy"); headers.add("student"); headers.add("slice"); headers.add("studentSteps"); headers.add("hash"); headers.add("steps");
		for (int i = 0; i < AutoGrader.graders.length; i++) headers.add("test" + i);
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(headers.toArray(new String[headers.size()])));

		int skip = SKIP;
		int max = MAX;
		
		double[] grades = new double[] { 0, 1 };
		String[] names = new String[] { "Hint All", "Hint Exemplar" };
		
		HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (skip-- > 0) {
				continue;
			}

			if (--max < 0) break;

			System.out.println(student);

			List<Node> nodes = nodeMap.get(student);

			AtomicInteger count = new AtomicInteger(0);
			int total = 0;

			List<Completion> completions = new ArrayList<>();
			for (int i = 0; i < grades.length; i++) {
				SubtreeBuilder builder = subtree.buildGraph(student, grades[0]);

				for (int slice = 0; slice < SLICES; slice++) {
					int index = nodes.size() * slice / SLICES;
					Node node = nodes.get(index);
					Completion completion = new Completion(builder, node, slice, nodes.size() - index - 1, names[i]);
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
	
	private static class Completion {
		private final SubtreeBuilder builder;
		private final Node startState;
		private final int slice, studentSteps;
		private final String policy;
		
		public Node endState;
		public HashMap<String, Boolean> grade;
		public int steps;
		
		public Completion(SubtreeBuilder builder, Node startState, int slice, int studentSteps, String policy) {
			this.builder = builder;
			this.startState = startState;
			this.slice = slice;
			this.studentSteps = studentSteps;
			this.policy = policy;
		}
		
		public void writeCSV(CSVPrinter printer, String student) throws IOException {
			int extraCols = 6;
			Object[] row = new Object[AutoGrader.graders.length + extraCols];
			row[0] = policy; row[1] = student; row[2] = slice; row[3] = studentSteps;
			row[4] = "H" + endState.hashCode(); row[5] = steps;
			
			for (int i = 0; i < AutoGrader.graders.length; i++) {
				row[i + extraCols] = grade.get(AutoGrader.graders[i].name());
			}
			printer.printRecord(row);
			printer.flush();
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
			steps = 0;
			Node state = startState;
			while (steps < MAX_STEPS) {
//				System.out.println(state);
				Hint hint = builder.getFirstHint(state);
				if (hint == null) break;
				state = hint.outcome().root();
				steps++;
			}
			
			endState = state;
			grade = AutoGrader.grade(endState);
		}
	}
}
