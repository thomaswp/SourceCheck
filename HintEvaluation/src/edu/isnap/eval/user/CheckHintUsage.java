package edu.isnap.eval.user;

import static edu.isnap.dataset.AttemptAction.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import distance.RTED_InfoTree_Opt;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.ctd.hint.HintFactoryMap.VectorHint;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.dataset.Grade;
import edu.isnap.datasets.Fall2015;
import edu.isnap.eval.AutoGrader;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.eval.util.Prune;
import edu.isnap.eval.util.Spreadsheet;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;
import util.LblTree;

public class CheckHintUsage {

	private static final long MIN_DURATON = 5 * 60 * 1000;
	private final static int IDLE_DURATION = 60;
	private final static int SKIP_DURATION = 60 * 5;

	public static void main(String[] args) throws IOException {
		writeHints(Fall2015.instance);
	}

	private static boolean isValidSubmission(AssignmentAttempt attempt) {
		if (attempt == null || !attempt.isLikelySubmitted() || attempt.size() == 0) return false;

		// Also ignore any that are shorted than then minimum duration (5m)
		long duration = attempt.rows.getLast().timestamp.getTime() -
				attempt.rows.getFirst().timestamp.getTime();
		if (duration < MIN_DURATON) return false;

		return true;
	}

	public static void writeHints(Dataset dataset) throws FileNotFoundException, IOException {
		Spreadsheet attempts = new Spreadsheet();
		Spreadsheet hints = new Spreadsheet();
		Assignment[] all = dataset instanceof Fall2015 ?
				Fall2015.All_WITH_SUBMISSIONS_ONLY : dataset.all();
		for (Assignment assignment : all) {
			writeHints(assignment, attempts, hints);
		}
		attempts.write(dataset.analysisDir() + "/attempts.csv");
		hints.write(dataset.analysisDir() + "/hints.csv");
	}

	public static void writeHints(Assignment assignment) throws FileNotFoundException, IOException {
		Spreadsheet attempts = new Spreadsheet();
		Spreadsheet hints = new Spreadsheet();
		writeHints(assignment, attempts, hints);
		attempts.write(assignment.analysisDir() + "/attempts.csv");
		hints.write(assignment.analysisDir() + "/hints.csv");
	}

	@SuppressWarnings("unused")
	private static void writeHints(Assignment assignment, Spreadsheet attemptsSheet,
			Spreadsheet hintsSheet)
			throws FileNotFoundException, IOException {
		System.out.println("Writing: " + assignment);

		// Get all submitted attempts at the assignment
		Map<String, AssignmentAttempt> attempts =
				assignment.loadAllSubmitted(Mode.Use, false, true);

		// Iterate over all submissions
		for (String attemptID : attempts.keySet()) {

			AssignmentAttempt attempt = attempts.get(attemptID);
			Grade grade = attempt.grade;
			if (grade != null && grade.outlier) continue;

			int nHints = 0, nUnchangedHints = 0, nDuplicateHints = 0, nThumbsUp = 0,
					nThumbsDown = 0, nHintsFollowed = 0, nHintsCloser = 0;
			int nObjectiveHints = 0, nObjectiveHintsFollowed = 0;
			int nTestScriptHints = 0, nTestScriptHintsFollowed = 0;
			int nBlockRuns = 0, nFlagRuns = 0;
			boolean hasLogs = true;


			// For any that attempt for which we have no logs, we use an empty assignment
			// attempt, which results in 0 for almost every column, but still includes the grades
			if (!isValidSubmission(attempt)) {
				attempt = new AssignmentAttempt(attemptID, attempt.grade);
				hasLogs = false;
			}

			List<LblTree> studentTrees = new LinkedList<>();

			Tuple<Node,Node> lastHint = null;

			Node lastHintNode = null;
			String lastHintData = null;
			Snapshot code = null;

			int edits = 0;
			for (int i = 0; i < attempt.size(); i++) {
				if (attempt.rows.get(i).snapshot != null) edits++;
			}
			long startTime = attempt.size() == 0 ? -1 : attempt.rows.getFirst().timestamp.getTime();
			long endTime =  attempt.size() == 0 ? -1 : attempt.rows.getLast().timestamp.getTime();

			// Calculate time statistics
			int activeTime = 0;
			int idleTime = 0;
			int workSegments = 0;
			long lastTime = 0;

			for (AttemptAction action : attempt) {

				if (action.id > attempt.submittedActionID) {
					throw new RuntimeException("Attempt " + attempt.id +
							" has actions after submission.");
				}

				long time = action.timestamp.getTime() / 1000;

				if (lastTime == 0) {
					lastTime = time;
					workSegments++;
					continue;
				}

				int duration = (int) (time - lastTime);
				if (duration < SKIP_DURATION) {
					int idleDuration = Math.max(duration - IDLE_DURATION, 0);
					activeTime += duration - idleDuration;
					idleTime += idleDuration;
				} else {
					workSegments++;
				}

				lastTime = time;
			}

			int edit = 0;

			// Iterate through each row of the solution path
			for (int i = 0; i < attempt.size(); i++) {
				AttemptAction row = attempt.rows.get(i);

				// If this row had an update to the code, update it
				if (row.snapshot != null) {
					code = row.snapshot;
					edit++;
				}

				// If we haven't seen a snapshot, just skip (this can happen due to prequels cutting
				// off logging or other logging errors)
				if (code == null) continue;

				// Get the student's current code and turn it into a tree
				Node node = SimpleNodeBuilder.toTree(code, true);

				// TODO: update using active time
				double timePerc = (double)(row.timestamp.getTime() - startTime) /
						(endTime - startTime);

				HashMap<String, Boolean> autograde = AutoGrader.grade(node);

				// Check if this action was showing a hint
				String action = row.message;
				if (SHOW_HINT_MESSAGES.contains(action)) {
					nHints++;

					// Get the data from this event
					JSONObject data = new JSONObject(row.data);


//					System.out.println("S" + nStudents + "H" + studentTrees.size());
//					System.out.println(code.toCode());
//					System.out.println(node.prettyPrint());

					LblTree tree = Prune.removeSmallerScripts(node).toTree();
					studentTrees.add(tree);

					// Find the parent node that this hint affects
					Node parent = findParent(row.message, code, node, data);

					// It shouldn't be null (and isn't for this dataset)
					if (parent == null) {
						System.out.println(node.prettyPrint());
						System.out.println(data);
						findParent(node, data);
						throw new RuntimeException("Parent shouldn't be null :/");
					}

					// Read the list of nodes that the hint is telling to use for the parent's new
					// children
					JSONArray toArray = data.getJSONArray("to");
					String[] to = new String[toArray.length()];
					for (int j = 0; j < to.length; j++) to[j] = toArray.getString(j);

					JSONArray fromArray;
					if (data.has("from")) {
						fromArray = data.getJSONArray("from");
					} else {
						fromArray = data.getJSONArray("fromList").getJSONArray(0);
					}
					String[] from = new String[fromArray.length()];
					for (int j = 0; j < from.length; j++) from[j] = fromArray.getString(j);
					// And apply this to get a new parent node
					Node hintOutcome = VectorHint.applyHint(parent, to);

					boolean delete = false;
					for (String f : from) {
						boolean kept = false;
						for (String t : to) {
							if (t.equals(f)) {
								kept = true;
								break;
							}
						}
						if (!kept) {
							delete = true;
							break;
						}
					}
					int nodeChange = hintOutcome.size() - parent.size();

					// Grade the node after applying the hint
					HashMap<String, Boolean> hintGrade = AutoGrader.grade(hintOutcome.root());

					String objective = null;
					// Check if applying a hint will complete an objective
					for (String key : autograde.keySet()) {
						if (!autograde.get(key) && hintGrade.get(key)) {
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

					lastHint = new Tuple<>(parent, hintOutcome);
					// For debugging these hints
//					System.out.println("  " + parent + "\n->" + hintOutcome + "\n");

					boolean gotCloser = false;
					boolean gotPartial = false;
					boolean gotObjective = false;

					// Look ahead for hint application in the student's code
					int steps = 0;
					for (int j = i+1; j < attempt.size(); j++) {
						// Get the next row with a new snapshot
						AttemptAction nextRow = attempt.rows.get(j);
						Snapshot nextCode = nextRow.snapshot;
						// if the row does not have a snapshot, skip this row and do not count
						// into steps
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
					}
					if (gotCloser) nHintsCloser++;
					if (gotPartial) {
						nHintsFollowed++;
					}
					if (gotObjective) nObjectiveHintsFollowed++;

					boolean duplicate = false;
					boolean unchanged;
					if (unchanged = node.equals(lastHintNode)) {
						nUnchangedHints++;
						if (duplicate = row.data.equals(lastHintData)) {
							nDuplicateHints++;
						}
					}
					lastHintNode = node;
					lastHintData = row.data;

					hintsSheet.newRow();
					hintsSheet.put("dataset", assignment.dataset.getName());
					hintsSheet.put("assignment", assignment.name);
					hintsSheet.put("id", attemptID);
					hintsSheet.put("type", action.replace("SnapDisplay.show", "").replace("Hint", ""));
					hintsSheet.put("editPerc", (double)edit / edits);
					hintsSheet.put("timePerc", timePerc);
					hintsSheet.put("followed", gotPartial ? 1 : 0);
					hintsSheet.put("obj", objective == null ? "" : objective);
					hintsSheet.put("objComplete", objective == null ? "" : (gotObjective ? 1 : 0));
					hintsSheet.put("delete", delete ? 1 : 0);
					hintsSheet.put("change", nodeChange);
					hintsSheet.put("unchanged", unchanged ? 1 : 0);
					hintsSheet.put("duplicate", duplicate ? 1 : 0);

					long time = row.timestamp.getTime();

					long dismissTime = 0;
					boolean done = false;
					for (int j = i + 1; j < attempt.size(); j++) {
						AttemptAction r = attempt.rows.get(j);
						if (r.message.equals("HintDialogBox.done")) done = true;
						if (r.message.equals(HINT_DIALOG_DESTROY)) {
							dismissTime = r.timestamp.getTime();
							break;
						}
					}
					int duration = (int)(dismissTime - time) / 1000;
					hintsSheet.put("duration", duration);
					hintsSheet.put("done", done ? 1 : 0);


					long nextActionTime = time;
					if (i < attempt.size() - 1) nextActionTime =
							attempt.rows.get(i + 1).timestamp.getTime();
					int pause = (int)(nextActionTime - time) / 1000;
					hintsSheet.put("pause", pause);

					Node n = parent;
					while (n.parent != null && !n.parent.hasType("sprite", "customBlock")) {
						n = n.parent;
					}
					int scriptSize = 0;
					boolean testScript = false;
					if (n.hasType("script") && n.parent != null) {
						scriptSize = n.size() - 1;
						final int children = n.children.size();
						if (n.parent.searchChildren(new Predicate() {
							@Override
							public boolean eval(Node node) {
								return node.hasType("script") && node.children.size() > children;
							}
						}) >= 0) {
							testScript = true;
							nTestScriptHints++;
							if (gotPartial) nTestScriptHintsFollowed++;
						}

					}
					hintsSheet.put("scriptSize", scriptSize);
					hintsSheet.put("testScript", testScript ? 1 : 0);
				}


				// Check if this action was dismissing a hint
				if (HINT_DIALOG_LOG_FEEDBACK.equals(action)) {
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

				if (IDE_GREEN_FLAG_RUN.equals(action)) nFlagRuns++;
				if (BLOCK_CLICK_RUN.equals(action)) nBlockRuns++;
			}

			attemptsSheet.newRow();
			// IDs
			attemptsSheet.put("dataset", assignment.dataset.getName());
			attemptsSheet.put("assignment", assignment.name);
			attemptsSheet.put("id", attemptID);
			attemptsSheet.put("logs", hasLogs);
			// Hint stats
			attemptsSheet.put("hints", nHints);
			attemptsSheet.put("unchangedHints", nUnchangedHints);
			attemptsSheet.put("duplicateHints", nDuplicateHints);
			attemptsSheet.put("followed", nHintsFollowed);
			attemptsSheet.put("closer", nHintsCloser);
			// Time stats
			attemptsSheet.put("active", activeTime);
			attemptsSheet.put("idle", idleTime);
			attemptsSheet.put("total", activeTime + idleTime);
			attemptsSheet.put("segments", workSegments);

			// Other stats
//			attemptsSheet.put("thumbsUp", nThumbsUp);
//			attemptsSheet.put("thumbsDown", nThumbsDown);
//			attemptsSheet.put("objHints", nObjectiveHints);
//			attemptsSheet.put("objHintsFollowed", nObjectiveHintsFollowed);
//			attemptsSheet.put("tsHints", nTestScriptHints);
//			attemptsSheet.put("tsHintsFollowed", nTestScriptHintsFollowed);
//			attemptsSheet.put("flagRuns", nFlagRuns);
//			attemptsSheet.put("blockRuns", nBlockRuns);

			if (grade != null) {
				int i = 0;
				attemptsSheet.put("grade", grade.average());
				for (Entry<String, Integer> entry : grade.tests.entrySet()) {
					attemptsSheet.put("Goal" + i++, entry.getValue());
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private void writeObjectives(Assignment assignment) throws FileNotFoundException, IOException {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);

		Spreadsheet objectives = new Spreadsheet();

		// Iterate over all submissions
		for (String attemptID : attempts.keySet()) {
			AssignmentAttempt path = attempts.get(attemptID);
			// Ignore any that weren't exported (and thus couldn't have been submitted)
			if (!isValidSubmission(path)) continue;

			Snapshot code = null;
			long startTime = path.rows.getFirst().timestamp.getTime();
			long endTime = path.rows.getLast().timestamp.getTime();


			Set<String> completedObjs = new HashSet<>();
			double lastCompleted = 0;

			// Iterate through each row of the solution path
			for (int i = 0; i < path.size(); i++) {
				AttemptAction row = path.rows.get(i);

				// If this row had an update to the code, update it
				if (row.snapshot != null) {
					code = row.snapshot;
				}

				// Get the student's current code and turn it into a tree
				Node node = SimpleNodeBuilder.toTree(code, true);

				double timePerc = (double)(row.timestamp.getTime() - startTime) /
						(endTime - startTime);

				HashMap<String, Boolean> grade = AutoGrader.grade(node);
				for (String obj : grade.keySet()) {
					if (grade.get(obj) && completedObjs.add(obj)) {
						objectives.newRow();
						objectives.put("id", attemptID);
						objectives.put("timePerc", timePerc);
						objectives.put("obj", obj);
						objectives.put("duration", timePerc - lastCompleted);

						lastCompleted = timePerc;
					}
				}
			}
		}

		objectives.write(assignment.analysisDir() + "/objs.csv");
	}

	public static <T> boolean contains(T[] array, T item) {
		for (T i : array) if (i.equals(item)) return true;
		return false;
	}

	@SuppressWarnings("unused")
	private static void outputDistance(PrintStream psSnapshot, PrintStream psHint, HashMap<String,
			AssignmentAttempt> submissions) throws IOException{
		// create column names
		List<String> headerSnapshot = new LinkedList<>();
		headerSnapshot.addAll(Arrays.asList("id","time","distance"));
		List<String> headerHint = new LinkedList<>();
		headerHint.addAll(Arrays.asList("id","time","distance","isTaken"));

		// create printer
		CSVPrinter prtSnapshot = new CSVPrinter(psSnapshot, CSVFormat.DEFAULT.withHeader(
				headerSnapshot.toArray(new String[headerSnapshot.size()])));
		CSVPrinter prtHint = new CSVPrinter(psHint, CSVFormat.DEFAULT.withHeader(
				headerHint.toArray(new String[headerHint.size()])));
		// loop through the submissions
		for (String key: submissions.keySet()) {
			AssignmentAttempt path = submissions.get(key);
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
				AttemptAction row = path.rows.get(i);

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
				if (row.message.equals("Block.grabbed")) isSnapshotChanged = false;


				// if snapshot is changed, calculate distance, record distance, time difference,
				// etc.
				if (isSnapshotChanged) {
					code = row.snapshot;
					snapshotNode = SimpleNodeBuilder.toTree(code, true);

					LblTree lblTree = Prune.removeSmallerScripts(snapshotNode).toTree();
					dis = opt.nonNormalizedTreeDist(lblFinalSubmission, lblTree);

					Date snapshotTime = row.timestamp;
					long diffTime = snapshotTime.getTime() - initTime.getTime();
					double diffTimeSec = diffTime / 1000.0;
					double elTime = diffTimeSec;

					Object[] rowSnapshot = new Object[3];
					rowSnapshot[0] = key;
					rowSnapshot[1] = elTime;
					rowSnapshot[2] = dis;

					// System.out.println("key: " + key + "; dis: " + dis + "; elTime: " + elTime + ";");
					// print record
					prtSnapshot.printRecord(rowSnapshot);
				}

				// if this is a hint, record time difference, distance (is Taken)
				if (SHOW_HINT_MESSAGES.contains(row.message)) {
					Date hintTime = row.timestamp;
					long diffTime = hintTime.getTime() - initTime.getTime();
					double diffTimeSec = diffTime / 1000.0;
					double elTime = diffTimeSec;

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
		prtHint.close();
	}

	@SuppressWarnings("unused")
	private static void outputGrades(PrintStream ps, HashMap<String, AssignmentAttempt> submissions,
			List<Integer> studentHintCounts, List<Integer> studentFollowedCounts)
					throws IOException {
		List<String> header = new LinkedList<>();
		header.addAll(Arrays.asList("id", "requested", "followed", "grade"));
		for (Grader g : AutoGrader.graders)	header.add(g.name());

		CSVPrinter printer = new CSVPrinter(ps, CSVFormat.DEFAULT.withHeader(
				header.toArray(new String[header.size()])));
		int i = 0;
		for (String key : submissions.keySet()) {
			AssignmentAttempt path = submissions.get(key);
			if (!isValidSubmission(path)) continue;

			Map<String, Integer> grade = path.grade.tests;
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
				row[col++] = grade.get(g.name());
			}
			printer.printRecord(row);

			i++;
		}

		printer.close();

	}

	@SuppressWarnings("unused")
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
	 * @param message The message for this hint
	 * @param snapshot The latest snapshot when the hint was given
	 * @param root The root node of the student's whole code
	 * @param data The data from the showHint event
	 * @return The node for the parent
	 */
	public static Node findParent(String message, Snapshot snapshot, Node root,
			JSONObject data) {
		Node parent = findParent(root, data);

		// Hack for custom block structure hints that failed to log rootTypes
		if (parent == null && "SnapDisplay.showStructureHint".equals(message)) {
			if (snapshot.editing != null) {
				parent = root.searchForNodeWithID(snapshot.editing.getID());
			}
		}

		return parent;
	}

	private static Node findParent(Node root, JSONObject data) {
		String[] ids = new String[] {
				"parentID", "rootID", "rootType"
		};

		Node parent = null;
		for (String id : ids) {
			Object value = getValue(data, id);
			if (value != null) {
				String parentID = value.toString();
				parent = root.searchForNodeWithID(parentID);
				break;
			}
		}

		if (parent != null && data.has("index") && !parent.hasType("script")) {
			if (!data.isNull("parentID")) {
				int index = data.getInt("index");
				parent = parent.children.get(index);
			} else {
				parent = parent.parent;
			}
		}

		if (parent != null && parent.children.size() == 1 &&
				parent.children.get(0).hasType("list")) {
			return parent.children.get(0);
		}

		return parent;
	}
}
