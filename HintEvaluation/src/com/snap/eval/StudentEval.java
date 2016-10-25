package com.snap.eval;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.snap.eval.util.Prune;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;
import com.snap.parser.Assignment;
import com.snap.parser.AttemptAction;
import com.snap.parser.Spring2016;
import com.snap.parser.AssignmentAttempt;
import com.snap.parser.Store.Mode;

public class StudentEval {
	
	public final static int MAX = 100;
	
	public static void main(String[] args) throws IOException {
		eval(Spring2016.GuessingGame1);
	}
	
	private static void eval(Assignment assignment) throws IOException {

		Map<String, AssignmentAttempt> paths = assignment.load(Mode.Use, true);
		
		for (int i = 0; i < 2; i++) {
			int max = MAX;
				
			HashSet<String> seen = new HashSet<>();
			HashSet<String> solutions = new HashSet<>();
			int totalNoDouble = 0;
			int total = 0;
			int students = 0;
			
			int pass0 = 0;
			
			double totalGrade = 0;
			int perfectGrade = 0;
			double minGrade = 1;
			
			for (String student : paths.keySet()) {
				if (assignment.ignore(student)) continue;
				if (--max < 0) break;
				
				AssignmentAttempt solutionPath = paths.get(student);
				List<Node> nodes = new LinkedList<>();
				for (AttemptAction r : solutionPath) nodes.add(SimpleNodeBuilder.toTree(r.snapshot, true));
				
				if (i == 1) nodes = Prune.removeSmallerScripts(nodes);
				HashSet<String> studentSet = new HashSet<>();
				

				Node solution = nodes.get(nodes.size() - 1);				
				double grade;
				if (solutionPath.grade != null) {
					grade = solutionPath.grade.average();
					if (solutionPath.grade.tests.get("Welcome player")) pass0++;
				} else {
					grade = AutoGrader.numberGrade(solution);
					if (AutoGrader.graders[0].pass(solution)) pass0++;
				}
				totalGrade += grade;
				minGrade = Math.min(grade, minGrade);
				if (grade == 1) perfectGrade++;
				
				for (Node node : nodes) {
					String ns = node.toCanonicalString();
					seen.add(ns);
					studentSet.add(ns);
				}
	
				total += nodes.size();
				totalNoDouble += studentSet.size();
				solutions.add(nodes.get(nodes.size() - 1).toCanonicalString());
				students++;

			}
			
			System.out.println("Pass0: " + pass0 + "/" + students);
			System.out.println("Unique: " + seen.size() + "/" + total);
			System.out.println("Unique no double: " + seen.size()  + "/" +  totalNoDouble);
			System.out.println("Unique solutions: " + solutions.size()  + "/" +  students);
			System.out.printf("Mean grade: %.03f\n", (totalGrade / students));
			System.out.println("Perfect grades: " + perfectGrade);
			System.out.println("Min grade: " + minGrade);
		}
	}
}
