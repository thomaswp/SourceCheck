package com.snap.graph.subtree;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;
import com.snap.graph.data.Node.Predicate;
import com.snap.graph.data.Node.TypePredicate;
import com.snap.parser.DataRow;
import com.snap.parser.Grade;
import com.snap.parser.SnapParser;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store;

public class AutoGrader {
	public final String dataDir, assignment;
	
	private final HashMap<Grade, Node> graded = new HashMap<Grade, Node>();
			
	public AutoGrader(String dataDir, String assignment) throws IOException {
		this.dataDir = dataDir;
		this.assignment = assignment;
		
		parseStudents();
	}
	
	private void parseStudents() throws IOException {
		SnapParser parser = new SnapParser(dataDir, Store.Mode.Use);
		HashMap<String, SolutionPath> students = parser.parseAssignment(assignment);
		
		for (String student : students.keySet()) {
			SolutionPath path = students.get(student);
			if (!path.exported) continue;
			if (path.grade == null) {
				System.err.println("No grade for: " + student);
				continue;
			}

			if (path.grade.outlier) continue;
			
			Snapshot last = null;
			for (DataRow row : path) {
				last = row.snapshot;
			}
			
			if (last == null) continue;
			graded.put(path.grade, SimpleNodeBuilder.toTree(last, true));
		}
	}
	
	public double verify(Grader grader) {
		int correct = 0, total = 0;
		
		String test = grader.name();
		for (Entry<Grade, Node> pair : graded.entrySet()) {
			Grade grade = pair.getKey();
			Node node = pair.getValue();
			
			Boolean pass = grade.tests.get(test);
			if (pass == null) continue;
			
			total++;
			boolean graderPass = grader.pass(node);
			if (pass == graderPass) correct++;
			else {
				System.out.println(grader.name() + " (" + pass + " vs " + graderPass + "): " + grade.id + " (" + grade.gradedID + ")");
//				System.out.println(((Snapshot)node.tag).toCode(true));
			}
		}
		
		return (double) correct / total;
	}
	
	public interface Grader {
		String name();
		boolean pass(Node node);
	}
	
	private static class WelcomePlayer implements Grader {

		@Override
		public String name() {
			return "Welcome player";
		}
		
		private final static Predicate backbone = 
				new Node.BackbonePredicate("snapshot", "stage", "sprite", "script");
		private final static Predicate isGreeting = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("doSayFor") && node.childHasType("literal", 0);
				
			}
		};
		private final static Predicate hasGreeting = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				int say = node.searchChildren(isGreeting);
				return say >= 0 && (ask <= 0 || say < ask); 
			}
		};
		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasGreeting); 
		
		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}
	
	private static class AskName implements Grader {
		@Override
		public String name() {
			return "Ask name";
		}
		
		private final static Predicate backbone = 
				new Node.BackbonePredicate("snapshot", "stage", "sprite", "script");
		
		private final static Predicate hasAskName = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				int doUntil = node.searchChildren(new Node.TypePredicate("doUntil"));
				
				if (doUntil >= 0) {
					return ask >= 0 && ask < doUntil;
				} else {
					return ask >= 0;
				}
			}
		};
		
		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasAskName);
		
		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}
	
	private static class GreetByName implements Grader {
		@Override
		public String name() {
			return "Greet by name";
		}
		
		// TODO: check for two say statements (14113)
		
		private final static Predicate backbone = 
				new Node.BackbonePredicate("snapshot", "stage", "sprite", "script");
		private final static Predicate isJoin = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (!node.hasType("reportJoinWords") || node.children.size() != 1) return false;
				Node list = node.children.get(0);
				return list.childHasType("literal", 0) && list.childHasType("getLastAnswer", 1);
			}
			
		};
		private final static Predicate isSetVariableToAnswer = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("doSetVar") && node.childHasType("getLastAnswer", 1);
			}
		};
		private final static Predicate isJoinVariable = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (!node.hasType("reportJoinWords") || node.children.size() != 1) return false;
				Node list = node.children.get(0);
				return list.childHasType("literal", 0) && list.childHasType("var", 1);
			}
			
		};
		private final static Predicate isGreetByName = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("doSayFor") && isJoin.eval(node.children.get(0));
				
			}
		};
		private final static Predicate isGreetByNameVariable = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("doSayFor") && isJoinVariable.eval(node.children.get(0));
				
			}
		};
		private final static Predicate hasGreeting = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				if (ask < 0) return false;
				int say = node.searchChildren(isGreetByName, ask + 1);
				if (say > ask) return true;
				
				int var = node.searchChildren(isSetVariableToAnswer);
				if (var < ask) return false;
				say = node.searchChildren(isGreetByNameVariable, ask + 1);
				return say > var;
			}
		};
		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasGreeting); 
		
		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}
	
	public static void main(String[] args) throws IOException {
		AutoGrader grader = new AutoGrader("../data/csc200/fall2015", "guess1Lab");
		
		Grader[] graders = new Grader[] {
				new WelcomePlayer(),
				new GreetByName(),
				new AskName()
		};
		
		for (Grader g : graders) {
			System.out.println(g.name() + ": " + grader.verify(g));
		}
		
	}
}
