package com.snap.eval.grades;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.snap.data.Snapshot;
import com.snap.eval.grades.AutoGrader.Grader;
import com.snap.eval.policy.DirectEditPolicy;
import com.snap.eval.policy.HintFactoryPolicy;
import com.snap.eval.policy.HintPolicy;
import com.snap.eval.policy.StudentPolicy;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class GradeEval {
	
	public static void main(String[] args) throws FileNotFoundException {
		
		String dir = "../data/csc200/fall2015";
		String assignment = "guess1Lab";
		
		Date maxTime = new GregorianCalendar(2015, 8, 18).getTime();
		SnapSubtree subtree = new SnapSubtree(dir, assignment, maxTime, new HintFactoryMap());
		
		Snapshot solution = Snapshot.parse(new File(dir + "/solutions/", assignment + ".xml"));
		Node solutionNode = SimpleNodeBuilder.toTree(solution, true);
		DirectEditPolicy solutionPolicy = new DirectEditPolicy(solutionNode);
		
		Score[] scoreTotals = new Score[] {
				new Score("Hint All", null),
				new Score("Hint Exemplar", null),
				new Score("Direct Ideal", null),
				new Score("Direct Student", null),
				new Score("Student Next", null)
		};
		
		int skip = 1;
		int max = 10;
		
		HashMap<String,List<Node>> nodeMap = subtree.nodeMap();
		for (String student : nodeMap.keySet()) {
			if (--max <= 0) break;
			
			if (skip > 0) {
				skip--;
				continue;
			}
			
			System.out.println(student);
			
			List<Node> nodes = nodeMap.get(student);		
			SubtreeBuilder builder0 = subtree.buildGraph(student, 0);
			SubtreeBuilder builder1 = subtree.buildGraph(student, 1);
			
			Score[] scores = new Score[] {
					new Score("Hint All", new HintFactoryPolicy(builder0)),
					new Score("Hint Exemplar", new HintFactoryPolicy(builder1)),
					new Score("Direct Ideal", solutionPolicy),
					new Score("Direct Student", new DirectEditPolicy(nodes.get(nodes.size() - 1))),
					new Score("Student Next", new StudentPolicy(nodes))
			};
			
			for (Node node : nodes) {
				HashMap<String,Boolean> grade = AutoGrader.grade(node);
				
				for (Score score : scores) {
					score.update(node, grade);
				}
			}

			for (int i = 0; i < scores.length; i++) {
				Score score = scores[i];
				scoreTotals[i].add(score);
//				score.print();
			}
		}
		
		for (Score score : scoreTotals) {
			score.print();
		}
	}
	
	private static class Score {
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
		
		public void print() {
			System.out.println(name + ":");
			for (String obj : outcomes.keySet()) {
				Tuple<Integer, Integer> outcome = outcomes.get(obj);
				System.out.println(obj + ": " + outcome);
			}
			System.out.println("Total: " + totalSteps);
			System.out.println();
		}

		private void add(Score score) {
			for (String key : score.outcomes.keySet()) {
				Tuple<Integer, Integer> outcome = outcomes.get(key);
				Tuple<Integer, Integer> otherOutcome = score.outcomes.get(key);
				outcome.x += otherOutcome.x;
				outcome.y += otherOutcome.y;
			}
			totalSteps += score.totalSteps;
		}
		
		private void update(Node node, HashMap<String,Boolean> grade) {
			Set<Node> steps = policy.nextSteps(node);
			
			for (Node next : steps) {				
				HashMap<String,Boolean> nextGrade = AutoGrader.grade(next);
				for (String obj : nextGrade.keySet()) {
					boolean a = grade.get(obj);
					boolean b = nextGrade.get(obj);
					
					if (a == true && b == false) {
//						Tuple<VectorHint, Boolean> record = new Tuple<VectorHint, Boolean>(vHint, false);
//						if (seen.add(record)) {
							outcomes.get(obj).x++;
//						}
					} else if (a == false && b == true) {
//						Tuple<VectorHint, Boolean> record = new Tuple<VectorHint, Boolean>(vHint, true);
//						if (seen.add(record)) {
							outcomes.get(obj).y++;
//						}
					}
				}
				totalSteps++;
			}
		}
	}
}
