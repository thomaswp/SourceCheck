package edu.isnap.eval;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Grade;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class AutoGrader {

	public static void main(String[] args) throws IOException {

		for (File xml : new File("tests").listFiles()) {
			if (!xml.getName().endsWith(".xml")) return;
			System.out.println(xml.getName() + ":");

			Snapshot snapshot = Snapshot.parse(xml);
			Node node = SimpleNodeBuilder.toTree(snapshot, false);

			for (Grader grader : PolygonGraders) {
				System.out.println(grader.name() + ": " + grader.pass(node));
			}
			System.out.println();
		}

//		Assignment assignments[] = new Assignment[] {
//				Fall2015.GuessingGame1,
//				Spring2016.GuessingGame1
//		};
//
//		for (Assignment assignment : assignments) {
//			System.out.println(assignment);
//			AutoGrader grader = new AutoGrader(assignment);
//
//			for (Grader g : graders) {
//				System.out.println(g.name() + ": " + grader.verify(g));
//			}
//			System.out.println("\n---------------------\n");
//		}

	}

	public final static Grader[] PolygonGraders = new Grader[] {
			new PolygonTest(),
	};

	public final static Grader[] graders = new Grader[] {
		new WelcomePlayer(),
		new AskName(),
		new GreetByName(),
		new StoreRandomNumber(),
		new LoopUntilGuessed(),
		new GetGuess(),
		new TooHigh(),
		new TooLow(),
		new ReportCorrect(),
	};

	private final HashMap<Grade, Node> graded = new HashMap<>();

	public AutoGrader(Assignment assignment) throws IOException {
		parseStudents(assignment);
	}

	public static HashMap<String, Boolean> grade(Node node) {
		HashMap<String, Boolean> grades = new HashMap<>();
		for (Grader g : graders) {
			grades.put(g.name(), g.pass(node));
		}
		return grades;
	}

	public static double numberGrade(Node node) {
		return numberGrade(grade(node));
	}

	public static double numberGrade(HashMap<String, Boolean> grade) {
		double g = 0;
		for (Boolean b : grade.values()) if (b) g++;
		g /= grade.size();
		return g;
	}

	private void parseStudents(Assignment assignment) throws IOException {
		Map<String, AssignmentAttempt> students = assignment.load(Mode.Use, true);

		for (String student : students.keySet()) {
			AssignmentAttempt path = students.get(student);
			if (!path.exported) continue;
			if (path.grade == null) {
				System.err.println("No grade for: " + student);
				continue;
			}

			if (path.grade.outlier) continue;

			Snapshot last = null;
			for (AttemptAction row : path) {
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

			boolean pass = grade.passed(test);

			total++;
			boolean graderPass = grader.pass(node);
			if (pass == graderPass) correct++;
			else {
				System.out.println(" > " + grader.name() + " (" + pass + " vs " + graderPass + "): " + grade.id + " (" + grade.gradedRow + ")");
//				System.out.println(((Snapshot)node.tag).toCode(true));
			}
		}

		return (double) correct / total;
	}

	public interface Grader {
		String name();
		boolean pass(Node node);
	}

	public static class PolygonTest implements Grader {

		@Override
		public String name() {
			return "test";
		}

		@Override
		public boolean pass(Node node) {
			return node.hasType("snapshot");
		}

	}

	public static class WelcomePlayer implements Grader {

		@Override
		public String name() {
			return "Welcome player";
		}

		private final static Predicate backbone =
				new Node.BackbonePredicate("sprite|customBlock", "script");
		private final static Predicate isGreeting = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("doSayFor", "bubble") && node.childHasType("literal", 0);

			}
		};
		private final static Predicate hasGreeting = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (node.children.size() < 3) return false;
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				int say = node.searchChildren(isGreeting);
				return say >= 0 && (ask < 0 || say < ask);
			}
		};
		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasGreeting);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	public static class AskName implements Grader {
		@Override
		public String name() {
			return "Ask name";
		}

		private final static Predicate backbone =
				new Node.BackbonePredicate("sprite|customBlock", "script");

		private final static Predicate hasAskName = new Predicate() {
			@Override
			public boolean eval(Node node) {

				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				int doUntil = node.searchChildren(new Node.TypePredicate("doUntil"));
				int doForever = node.searchChildren(new Node.TypePredicate("doForever"));

				if (doUntil >= 0) {
					return ask >= 0 && ask < doUntil;
				} else if (doForever >= 0){
					return ask >= 0 && ask < doForever;
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

	public static class GreetByName implements Grader {
		@Override
		public String name() {
			return "Greet by name";
		}

		private final static Predicate backbone =
				new Node.BackbonePredicate("sprite|customBlock", "script");
		private final static Predicate isJoin = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (!node.hasType("reportJoinWords") || node.children.size() != 1) return false;
				Node list = node.children.get(0);
				return list.childHasType("literal", 0) && list.childHasType("getLastAnswer", 1) ||
						list.childHasType("literal", 1) && list.childHasType("getLastAnswer", 0);
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
				return list.childHasType("literal", 0) && list.childHasType("var", 1) ||
						list.childHasType("literal", 1) && list.childHasType("var", 0);
			}
		};

		private final static Predicate isSay = new Node.TypePredicate("doSayFor", "bubble", "doAsk");

		private final static Predicate isGreetByName = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return isSay.eval(node) && node.children.size() > 0 && node.children.get(0).exists(isJoin);

			}
		};
		private final static Predicate isGreetByNameVariable = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return isSay.eval(node) && node.children.size() > 0 && node.children.get(0).exists(isJoinVariable);

			}
		};
		private final static Predicate hasGreeting = new Predicate() {
			@Override
			public boolean eval(Node node) {
				int ask = node.searchChildren(new Node.TypePredicate("doAsk"));
				if (ask < 0) return false;

				return testGreet(node, ask);
			}

			private boolean testGreet(Node node, int ask) {
				int say = node.searchChildren(isGreetByName, ask + 1);
				if (say >= 0) return true;

				int var = node.searchChildren(isSetVariableToAnswer, ask + 1);
				if (var >= 0) {
					say = node.searchChildren(isGreetByNameVariable, var + 1);
					if (say >= 0) return true;
				}

				// Check if it's nested inside a true if statement
				int doIf = node.searchChildren(new Node.TypePredicate("doIf"));
				if (doIf >= 0) {
					Node doIfNode = node.children.get(doIf);
					if (doIfNode.children.size() > 0 && !doIfNode.children.get(0).exists(new Node.TypePredicate("literal"))) {
						if (doIfNode.children.size() > 1) {
							if (testGreet(doIfNode.children.get(1), -1)) return true;
						}
					}
				}

				// Consecutive say("Hello"), say(answer)
				say = node.searchChildren(isSay, ask + 1);
				if (say < 0 || say == node.children.size() - 1) return false;
				Node say1 = node.children.get(say);
				Node say2 = node.children.get(say + 1);
				if (say1.children.size() == 0 || say2.children.size() == 0 || !isSay.eval(say2)) return false;
				return say1.childHasType("literal", 0) && say2.childHasType("getLastAnswer", 0) ||
						say2.childHasType("literal", 0) && say1.childHasType("getLastAnswer", 0);
			}
		};
		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasGreeting);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	public static class StoreRandomNumber implements Grader {

		@Override
		public String name() {
			return "Store random number";
		}

		private final static Predicate backbone =
				new Node.BackbonePredicate("sprite|customBlock", "script");

		private final static Predicate isReportRandom = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("reportRandom");
			}
		};

		private final static Predicate isStoreRandomNumber = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.hasType("doSetVar") && node.children.size() > 1  && isReportRandom.eval(node.children.get(1));
			}
		};

		private final static Predicate hasStoreRandomNumber = new Predicate() {

			@Override
			public boolean eval(Node node) {
				int doSetVar = node.searchChildren(isStoreRandomNumber);
				int doUntil = node.searchChildren(new Node.TypePredicate("doUntil"));
				int doForever = node.searchChildren(new Node.TypePredicate("doForever"));

				if (doUntil >= 0) {
					return doSetVar >= 0 && doSetVar < doUntil;
				} else if (doForever >= 0){
					return doSetVar >= 0 && doSetVar < doForever;
				} else {
					return doSetVar >= 0;
				}
			}

		};

		private final static Predicate test = new Node.ConjunctionPredicate(true, backbone, hasStoreRandomNumber);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}

	}


	public static class LoopUntilGuessed implements Grader {
		@Override
		public String name() {
			return "Loop until it's guessed";
		}

		private final static Predicate doUntilbackbone = new Node.BackbonePredicate("sprite|customBlock", "...", "script", "doUntil", "reportEquals");
		private final static Predicate doForeverbackbone = new Node.BackbonePredicate("sprite|customBlock", "...", "script", "doForever", "script");

		private final static Predicate isDoIfElse = new Node.TypePredicate("doIf", "doIfElse");
		private final static Predicate isDoStopThis = new Node.TypePredicate("doStopThis");

		private final static Predicate isStopCondition = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (!node.hasType("reportEquals")) return false;
				if (node.children.size() != 2) return false;

				String t1 = node.children.get(0).type();
				String t2 = node.children.get(1).type();
				return ("var".equals(t1) || "getLastAnswer".equals(t1)) &&
						("var".equals(t2) || "getLastAnswer".equals(t2));
			}
		};

		private final static Predicate doUntilCondition = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return isStopCondition.eval(node);
			};
		};

		private final static Predicate doForeverCondition = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return node.exists(new Predicate() {
					@Override
					public boolean eval(Node node) {
						return isDoIfElse.eval(node) &&
								node.exists(isStopCondition) &&
								node.exists(isDoStopThis);
					}
				});
			};
		};

		private final static Predicate doUntilTest = new Node.ConjunctionPredicate(true, doUntilbackbone, doUntilCondition);
		private final static Predicate doForeverTest = new Node.ConjunctionPredicate(true, doForeverbackbone, doForeverCondition);

		@Override
		public boolean pass(Node node) {
			return node.exists(doUntilTest) || node.exists(doForeverTest);
		}
	}

	public static class GetGuess implements Grader {
		@Override
		public String name() {
			return "Ask player for guess (in loop)";
		}

		private final static Predicate backboneDoUntil = new Node.BackbonePredicate(
				"sprite|customBlock", "...", "script", "doUntil", "...", "script", "doAsk");
		private final static Predicate backboneDoForever = new Node.BackbonePredicate(
				"sprite|customBlock", "...", "script", "doForever", "...", "script", "doAsk");
		private final static Predicate test = new Node.ConjunctionPredicate(false, backboneDoForever, backboneDoUntil);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	private abstract static class FeedbackGrader implements Grader {

		private  final static Predicate backbone = new Node.BackbonePredicate(
				"sprite|customBlock", "...", "script", "doUntil|doForever", "...", "script", "...");

		protected final static Predicate isResponse = new Node.TypePredicate("doSayFor", "doAsk", "bubble");

		private static boolean isTooHighLow(Node node, boolean tooHigh) {
			if (node.children.size() != 2) return false;
			int varIndex, answerIndex;
			if (node.hasType("reportLessThan")) {
				varIndex = tooHigh ? 0 : 1;
				answerIndex = tooHigh ? 1 : 0;
			} else if (node.hasType("reportGreaterThan")) {
				varIndex = tooHigh ? 1 : 0;
				answerIndex = tooHigh ? 0 : 1;
			} else {
				return false;
			}
			return node.childHasType("var", varIndex) &&
					(node.childHasType("getLastAnswer", answerIndex) ||
							node.childHasType("var", answerIndex));
		}

		protected final static boolean saysTooHighLow(Node node, boolean tooHigh) {
			if (!(node.hasType("doIf") || node.hasType("doIfElse"))) return false;
			if (!backbone.eval(node)) return false;
			if (node.children.size() < 2) return false;
			Node condition = node.children.get(0);

			// Make sure it's not nested in the opposite
			if (node.parent.index() == 1 && saysTooHighLow(node.parent.parent, !tooHigh)) return false;

			boolean hasCondition = isTooHighLow(condition, tooHigh);
			boolean hasInvCondition = isTooHighLow(condition, !tooHigh);
			if (hasCondition && node.children.get(1).searchChildren(isResponse) != -1) {
				return true;
			}
			if (node.hasType("doIfElse") && node.children.size() > 2) {
				return hasInvCondition && node.children.get(2).searchChildren(isResponse) != -1;
			}
			return false;
		}

		protected final static Predicate correctCondition = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (node.children.size() != 2) return false;
				if (!node.hasType("reportEquals")) return false;
				return (node.childHasType("var", 0) || node.childHasType("getLastAnswer", 0)) &&
						(node.childHasType("var", 1) || node.childHasType("getLastAnswer", 1));
			}
		};
	}

	public static class TooHigh extends FeedbackGrader {
		@Override
		public String name() {
			return "Tell if too high (in loop)";
		};

		private final static Predicate test = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return saysTooHighLow(node, true);
			}
		};

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	public static class TooLow extends FeedbackGrader {
		@Override
		public String name() {
			return "Tell if too low (in loop)";
		};

		private final static Predicate test = new Predicate() {
			@Override
			public boolean eval(Node node) {
				return saysTooHighLow(node, false);
			}
		};

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}
	}

	public static class ReportCorrect extends FeedbackGrader {

		@Override
		public String name() {
			return "Tell if correct";
		}

		private  final static Predicate backbone = new Node.BackbonePredicate(
				"sprite|customBlock", "...");

		private final static Predicate hasInLoop = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (!(node.hasType("doIf") || node.hasType("doIfElse"))) return false;
				if (!backbone.eval(node)) return false;
				if (node.children.size() < 2) return false;
				return (correctCondition.eval(node.children.get(0)) &&
						node.children.get(1).searchChildren(isResponse) != -1);
			};
		};

		private final Predicate hasOutOfLoop = new Predicate() {
			@Override
			public boolean eval(Node node) {
				if (!LoopUntilGuessed.doUntilCondition.eval(node)) return false;
				Node loop = node.parent;
				if (loop.exists(LoopUntilGuessed.isDoStopThis)) return false;
				int loopIndex = loop.parent.children.indexOf(loop);
				return loop.parent.searchChildren(isResponse, loopIndex + 1) != -1;
			};
		};

		private final Predicate test = new Node.ConjunctionPredicate(false, hasOutOfLoop, hasInLoop);

		@Override
		public boolean pass(Node node) {
			return node.exists(test);
		}

	}
}
