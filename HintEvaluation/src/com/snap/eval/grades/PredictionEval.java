package com.snap.eval.grades;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.snap.data.Snapshot;
import com.snap.eval.policy.DirectEditPolicy;
import com.snap.eval.policy.HintFactoryPolicy;
import com.snap.eval.policy.HintPolicy;
import com.snap.eval.util.PrintUpdater;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.parser.Grade;

public class PredictionEval {

	private final static int SKIP = 1, MAX = 3, LOOK_AHEAD = 5;
	
	public static void main(String[] args) throws IOException {
		
		String dir = "../data/csc200/fall2015";
		String assignment = "guess1Lab";
		
		predictionEval(dir, assignment);
	}
	
	public static void predictionEval(String dir, String assignment) throws FileNotFoundException, IOException {
		Snapshot solution = Snapshot.parse(new File(dir + "/solutions/", assignment + ".xml"));
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);
				
		eval(dir, assignment, "prediction", new ScoreConstructor() {
			@Override
			public Score[] construct(String student, List<Node> nodes, SnapSubtree subtree) {
				SubtreeBuilder builder0 = subtree.buildGraph(student, 0);
				SubtreeBuilder builder1 = subtree.buildGraph(student, 1);
				return new Score[] {
						new Score("Hint All", new HintFactoryPolicy(builder0)),
						new Score("Hint Exemplar", new HintFactoryPolicy(builder1)),
						new Score("Direct Ideal", solutionPolicy),
						new Score("Direct Student", new DirectEditPolicy(nodes.get(nodes.size() - 1))),
				};
			}
		});
	}
	
	private static void eval(String dir, String assignment, String test, ScoreConstructor constructor) throws IOException {
		
		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree(dir, assignment, maxTime, new HintFactoryMap());
		
		File outFile = new File(dir + "/anlysis/" + assignment + "/" + test + ".csv");
		outFile.getParentFile().mkdirs();
		List<String> headers = new LinkedList<>();
		headers.add("policy"); headers.add("student"); headers.add("grade"); headers.add("predicted"); headers.add("hints"); headers.add("actions");
		CSVPrinter printer = new CSVPrinter(new PrintStream(outFile), CSVFormat.DEFAULT.withHeader(headers.toArray(new String[headers.size()])));
				
		HashMap<String,Grade> gradeMap = subtree.gradeMap();
		
		int skip = SKIP;
		int max = MAX;
		
		HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (skip-- > 0) {
				continue;
			}

			Grade grade = gradeMap.get(student);
			if (grade == null) continue;
			
			if (--max < 0) break;
			
			System.out.println(student);
			
			List<Node> nodes = nodeMap.get(student);
			
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
					updater.update((total - (double)count.get()) / total);
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
		Score[] construct(String student, List<Node> nodes, SnapSubtree subtree);
	}
	
	protected static class Score {
		public final HintPolicy policy;
		public final String name;
		
		private int predicted;
		private int totalHints;
		private int totalActions;
		
		public Score(String name, HintPolicy policy) {
			this.name = name;
			this.policy = policy;
		}
		
		public void writeRow(CSVPrinter printer, String student, double grade) throws IOException {
			printer.printRecord(name, student, grade, predicted, totalHints, totalActions);
			printer.flush();
		}
		
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
				}
			}
			
			synchronized (this) {
				totalHints += hints;
				totalActions++;
				predicted += pred;
			}
		}
	}
}
