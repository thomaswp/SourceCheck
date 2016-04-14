package com.snap.eval.user;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import com.snap.data.Snapshot;
import com.snap.eval.Assignment;
import com.snap.eval.AutoGrader;
import com.snap.eval.AutoGrader.Grader;
import com.snap.eval.util.Prune;
import com.snap.graph.Alignment;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder.Tuple;
import com.snap.parser.DataRow;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store.Mode;

import distance.RTED_InfoTree_Opt;
import util.LblTree;

public class CheckHintUsage {
	
	// Actions of interest
	private final static String SHOW_SCRIPT_HINT = "SnapDisplay.showScriptHint";
	private final static String SHOW_BLOCK_HINT = "SnapDisplay.showBlockHint";
	private final static String SHOW_STRUCTURE_HINT = "SnapDisplay.showStructureHint";
	private final static String HINT_DIALOG_DONE = "HintDialogBox.done";

	private final static String PROCESS_HINTS = "HintProvider.processHints";
	
	private final static List<String> SHOW_HINT_MESSAGES = Arrays.asList(new String[] {
			SHOW_SCRIPT_HINT, SHOW_BLOCK_HINT, SHOW_STRUCTURE_HINT
	});
	
	private static final long MIN_DURATON = 5 * 60 * 1000;
	
	private static boolean isValidSubmission(SolutionPath path) {
		if (!path.exported) return false;
		
		// Also ignore any that are shorted than then minimum duration (5m) 
		long duration = path.rows.getLast().timestamp.getTime() - 
				path.rows.getFirst().timestamp.getTime();
		if (duration < MIN_DURATON) return false;
		
		return true;
	}
	
	public static void main(String[] args) throws IOException {
		
		// Get the name-path pairs of all projects we logged
		HashMap<String, SolutionPath> guessingGame = Assignment.Spring2016.GuessingGame1.load(Mode.Use, false);
		
		int nStudents = 0, nHints = 0, nThumbsUp = 0, nThumbsDown = 0, nHintsTaken = 0, nHintsParial = 0, nHintsCloser = 0;
		int nObjectiveHints = 0, nObjectiveHintsTaken = 0;
		int nStudentHint1 = 0, nStudentHint3 = 0;
		List<Integer> studentHintCounts = new LinkedList<Integer>(), studentFollowedCounts = new LinkedList<>();
		
		HashMap<String, LblTree> hintCodeTrees = new LinkedHashMap<>();
		
		// Iterate over all submissions
		for (String submission : guessingGame.keySet()) {
			SolutionPath path = guessingGame.get(submission);
			
			// Ignore any that weren't exported (and thus couldn't have been submitted)
			if (!isValidSubmission(path)) continue;
			
			// The number of student who exported project (presumably number of submissions)
			nStudents++;
			// number of hints requested by this student
			int nStudentHints = 0, nStudentFollowed = 0;
			
			List<LblTree> studentTrees = new LinkedList<LblTree>();
						
			Tuple<Node,Node> lastHint = null;
			
			Snapshot code = null;
			// Iterate through each row of the solution path
			for (int i = 0; i < path.size(); i++) {
				DataRow row = path.rows.get(i);
				
				// If this row had an update to the code, update it
				if (row.snapshot != null) code = row.snapshot;
				
				// Check if this action was showing a hint
				String action = row.action;
				if (SHOW_HINT_MESSAGES.contains(action)) {
					nHints++;
					nStudentHints++;
					
					// Get the data from this event
					JSONObject data = new JSONObject(row.data);
					
					// Get the student's current code and turn it into a tree
					Node node = SimpleNodeBuilder.toTree(code, true);

//					System.out.println("S" + nStudents + "H" + studentTrees.size());
//					System.out.println(code.toCode());
//					System.out.println(node.prettyPrint());
					
					LblTree tree = Prune.removeSmallerScripts(node).toTree();
					studentTrees.add(tree);
					
					HashMap<String,Boolean> grade = AutoGrader.grade(node);
					
					// Find the parent node that this hint affects
					Node parent = findParent(node, data);
					// It shouldn't be null (and isn't for this dataset)
					if (parent == null) {
						System.out.println(node.prettyPrint());
						System.out.println(data);
						findParent(node, data);
						throw new RuntimeException("Parent shouldn't be null :/");
					}
					
					// Read the list of nodes that the hint is telling to use for the parent's new children
					JSONArray toArray = data.getJSONArray("to");
					String[] to = new String[toArray.length()];
					for (int j = 0; j < to.length; j++) to[j] = toArray.getString(j);
					// And apply this to get a new parent node
					Node hintOutcome = VectorHint.applyHint(parent, to);
					
					// Grade the node after applying the hint
					HashMap<String, Boolean> hintGrade = AutoGrader.grade(hintOutcome.root());
					
					String objective = null; 
					// Check if applying a hint will complete an objective
					for (String key : grade.keySet()) {
						if (!grade.get(key) && hintGrade.get(key)) {
							objective = key;
							break;
						}
					}
					// record the number of hints requested that can complete an objective
					if (objective != null) nObjectiveHints++;
					
					// get the corresponding grader for the objective completed by hint
					Grader objectiveGrader = null;
					for (Grader g : AutoGrader.graders) 
						if (g.name().equals(objective)) 
							objectiveGrader = g;
					
					// Calculate original distance between student's code with the hint
					int originalHintDistance = Alignment.alignCost(parent.getChildArray(), to);
					
					lastHint = new Tuple<Node, Node>(parent, hintOutcome);
					// For debugging these hints
//					System.out.println("  " + parent + "\n->" + hintOutcome + "\n");
										
					boolean gotCloser = false;
					boolean gotPartial = false;
					boolean gotObjective = false;
					
					// Look ahead for hint application in the student's code
					int steps = 0;
					for (int j = i+1; j < path.size(); j++) {
						// Get the next row with a new snapshot
						DataRow nextRow = path.rows.get(j);
						Snapshot nextCode = nextRow.snapshot;
						// if the row does not have a snapshot, skip this row and do not count into steps
						if (nextCode == null)
							continue;
						steps++;
						
						// If we've looked more than n (5) steps in the future, give up
						if (steps > 5) break;
						
						// Find the same parent node and see if it matches the hint state
						Node nextNode = SimpleNodeBuilder.toTree(nextCode, true);
						Node nextParent = findParent(nextNode, data);
						if (nextParent == null) continue;

						int newDistance = Alignment.alignCost(nextParent.getChildArray(), to);
						if (newDistance < originalHintDistance) gotCloser = true;
						
						if (Arrays.equals(nextParent.getChildArray(), to)) {
							gotPartial = true;
						}
						
						if (objectiveGrader != null && objectiveGrader.pass(nextNode)) {
							gotObjective = true;
						}
						
						// TODO: Rather than just looking for an exact match, check if the student's code gets
						// closer to the hint or farther. 
						if (nextParent.equals(hintOutcome)) {
							nHintsTaken++;
							break;
						}
					}
					if (gotCloser) nHintsCloser++; 
					if (gotPartial) {
						nHintsParial++;
						nStudentFollowed++;
					}
					if (gotObjective) nObjectiveHintsTaken++;
				}
				
				
				// Check if this action was dismissing a hint
				if (HINT_DIALOG_DONE.equals(action)) {
					// TODO: inspect good and bad hints
					if (row.data.equals("[\"up\"]")) {
						nThumbsUp++;
					} else if (row.data.equals("[\"down\"]")) {
						nThumbsDown++;
						// To print thumbs-down hints
//						if (lastHint != null) {
//							System.out.println("Bad hint: " + lastHint.x + "\n       -> " + lastHint.y + "\n");
//						}
					}
				}
			}
			if (nStudentHints > 0) nStudentHint1++;
			if (nStudentHints > 2) nStudentHint3++;
			if (nStudentHints > 30) System.out.println(submission);
			studentHintCounts.add(nStudentHints);
			studentFollowedCounts.add(nStudentFollowed);
			
			if (nStudentHints <= 30) {
				for (int j = 0; j < studentTrees.size(); j++) {
					hintCodeTrees.put("S" + nStudents + "H" + j, studentTrees.get(j));
				}
			}
		}

		
		PrintStream ps = new PrintStream(Assignment.Spring2016.GuessingGame1.dataDir + "/grades.csv");
		outputGrades(ps, guessingGame, studentHintCounts, studentFollowedCounts);
		ps.close();
		
		// output distance between snapshots and final submission
		PrintStream psSnapshot = new PrintStream(Assignment.Spring2016.GuessingGame1.dataDir + "/snapshot.csv");
		PrintStream psHint = new PrintStream(Assignment.Spring2016.GuessingGame1.dataDir + "/hint.csv");
		outputDistance(psSnapshot,psHint,guessingGame);
		psSnapshot.close();
		psHint.close();
		
		// Print our results
		System.out.println("Submissions: " + nStudents);
		System.out.println("Total Hints Selected: " + nHints);
		System.out.println("Thumbs Up: " + nThumbsUp + "/" + nHints);
		System.out.println("Thumbs Down: " + nThumbsDown + "/" + nHints);
		System.out.println("Hints Taken: " + nHintsTaken + "/" + nHints);
		System.out.println("Hints Partial: " + nHintsParial + "/" + nHints);
		System.out.println("Hints Closer: " + nHintsCloser + "/" + nHints);
		System.out.println("Objective Hints: " + nObjectiveHints + "/" + nHints);
		System.out.println("Objective Hints Taken: " + nObjectiveHintsTaken + "/" + nObjectiveHints);
		System.out.println("Students got more than 1 hint: " + nStudentHint1 + "/" + nStudents);
		System.out.println("Students got more than 3 hint: " + nStudentHint3 + "/" + nStudents);
		Collections.sort(studentHintCounts);
		Collections.reverse(studentHintCounts);
		System.out.println("Students Hint count: " + studentHintCounts);
		
//		PrintStream ps = new PrintStream(Assignment.Spring2016.GuessingGame1.dataDir + "/hintsDis.csv");
//		outputMatrix(ps, hintCodeTrees);
//		ps.close();
	}
	
	private static void outputDistance(PrintStream psSnapshot, PrintStream psHint, HashMap<String, SolutionPath> submissions) throws IOException{
		// create column names
		List<String> headerSnapshot = new LinkedList<String>();
		headerSnapshot.addAll(Arrays.asList("id","time","distance"));
		List<String> headerHint = new LinkedList<String>();
		headerHint.addAll(Arrays.asList("id","time","distance","isTaken"));
		
		// create printer
		CSVPrinter prtSnapshot = new CSVPrinter(psSnapshot, CSVFormat.DEFAULT.withHeader(headerSnapshot.toArray(new String[headerSnapshot.size()])));
		CSVPrinter prtHint = new CSVPrinter(psHint, CSVFormat.DEFAULT.withHeader(headerHint.toArray(new String[headerHint.size()])));
		// loop through the submissions
		for (String key: submissions.keySet()) {
			SolutionPath path = submissions.get(key);
			// if not exported, continue
			if (!isValidSubmission(path)) continue;
			
			// for each submission, get initial time, final submission
			Node finalSubmission = null;
			Date initTime = null;
			
			// find final submission
			for (int j = path.size() - 1; j >= 0; j--) {
				Snapshot snapshot = path.rows.get(j).snapshot;
				if (snapshot != null) {
					finalSubmission = SimpleNodeBuilder.toTree(snapshot, true);
					break;
				}
			}
			
			LblTree lblFinalSubmission = Prune.removeSmallerScripts(finalSubmission).toTree();
			
			// get timestamp for the first row as initial time
			initTime = path.rows.get(0).timestamp;
			
			
			// loop through rows
			Snapshot code = null;
			Node snapshotNode = null;
			double dis = 1000.0;
			RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
			for (int i = 0; i < path.size(); i++) {
				DataRow row = path.rows.get(i);
				
				// check if snapshot is changed.
				boolean isSnapshotChanged = false;
				if (row.snapshot != null) {
					if (snapshotNode == null) {
						isSnapshotChanged = true;
					} else {
						Node node = SimpleNodeBuilder.toTree(row.snapshot, true);
						if (!snapshotNode.equals(node)) isSnapshotChanged = true;
					}
				}
				if (row.action.equals("Block.grabbed")) isSnapshotChanged = false;
				
				
				// if snapshot is changed, calculate distance, record distance, time difference, etc.
				if (isSnapshotChanged) {
					code = row.snapshot;
					snapshotNode = SimpleNodeBuilder.toTree(code, true);
					
					LblTree lblTree = Prune.removeSmallerScripts(snapshotNode).toTree();
					dis = opt.nonNormalizedTreeDist(lblFinalSubmission, lblTree);
					
					Date snapshotTime = row.timestamp;
					long diffTime = snapshotTime.getTime() - initTime.getTime();
					double diffTimeSec = diffTime / 1000.0;
					double elTime = (double) diffTimeSec;
					
					Object[] rowSnapshot = new Object[3];
					rowSnapshot[0] = key;
					rowSnapshot[1] = elTime;
					rowSnapshot[2] = dis;
					
					// System.out.println("key: " + key + "; dis: " + dis + "; elTime: " + elTime + ";");
					// print record
					prtSnapshot.printRecord(rowSnapshot);
				}
				
				// if this is a hint, record time difference, distance (is Taken)
				if (SHOW_HINT_MESSAGES.contains(row.action)) {
					Date hintTime = row.timestamp;
					long diffTime = hintTime.getTime() - initTime.getTime();
					double diffTimeSec = diffTime / 1000.0;
					double elTime = (double) diffTimeSec;
					
					Object[] rowHint = new Object[4];
					rowHint[0] = key;
					rowHint[1] = elTime;
					rowHint[2] = dis;
					rowHint[3] = "null";
					
					//System.out.println("key: " + key + "; dis: " + dis + "; elTime: " + elTime + ";");
					
					prtHint.printRecord(rowHint);
				}
			}
		}
		// close printer
		prtSnapshot.close();
	}

	private static void outputGrades(PrintStream ps, HashMap<String, SolutionPath> submissions, List<Integer> studentHintCounts,
			List<Integer> studentFollowedCounts) throws IOException {
		List<String> header = new LinkedList<>(); 
		header.addAll(Arrays.asList("id", "requested", "followed", "grade"));
		for (Grader g : AutoGrader.graders)	header.add(g.name());
		
		CSVPrinter printer = new CSVPrinter(ps, CSVFormat.DEFAULT.withHeader(header.toArray(new String[header.size()])));
		int i = 0;
		for (String key : submissions.keySet()) {
			SolutionPath path = submissions.get(key);
			if (!isValidSubmission(path)) continue;
			
			HashMap<String, Boolean> grade = path.grade.tests;
			double numberGrade = path.grade.average();
//			if (numberGrade < 0.5f) {
//				System.out.println(key + ": " + grade);
//				System.out.println(((Snapshot)code.tag).toCode());
//			}
			
			Object[] row = new Object[grade.size() + 4];
			int col = 0;
			row[col++] = key; row[col++] = studentHintCounts.get(i); 
			row[col++] = studentFollowedCounts.get(i); row[col++] = numberGrade;
			
			for (Grader g : AutoGrader.graders) {
				row[col++] = grade.get(g.name()) ? 1 : 0;
			}
			printer.printRecord(row);
			
			i++;
		}
		
		printer.close();
		
	}

	private static void outputMatrix(PrintStream out, HashMap<String, LblTree> trees) {
		String[] labels = trees.keySet().toArray(new String[trees.size()]);
		for (String label : labels) {
			out.print("," + label);
		}
		out.println();
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
		for (String label1 : labels) {
			out.print(label1);
			LblTree tree1 = trees.get(label1);
			for (String label2 : labels) {
				LblTree tree2 = trees.get(label2);
				double dis = opt.nonNormalizedTreeDist(tree1, tree2);
				int disInt = (int)(Math.round(dis));
				out.print("," + disInt);
			}
			out.println();
		}
	}

	private static Object getValue(JSONObject obj, String key) {
		if (!obj.has(key) || obj.isNull(key)) return null;
		return obj.get(key);
	}
	
	/**
	 * Finds the Node that was the parent for this hint, i.e. the hint
	 * is telling us to change this node's children.
	 * @param root The root node of the student's whole code
	 * @param data The data from the showHint event
	 * @return The node for the parent
	 */
	public static Node findParent(Node root, JSONObject data) {
		String[] ids = new String[] {
				"parentID", "rootID", "rootType"
		};
		
		Node parent = null;
		for (String id : ids) {
			Object parentID = getValue(data, id);
			if (parentID != null) {
				parent = root.searchForNodeWithID(parentID);
				break;
			}	
		}
		
		if (parent != null && data.has("index")) {
			if (!data.isNull("parentID")) {
				int index = data.getInt("index");
				parent = parent.children.get(index);
			} else {
				parent = parent.parent;
			}
		}
		
		if (parent != null && parent.children.size() == 1 && parent.children.get(0).hasType("list")) {
			return parent.children.get(0);
		}
		
		return parent;
	}
}
