package edu.isnap.eval.user;

import static edu.isnap.dataset.AttemptAction.HINT_DIALOG_DESTROY;
import static edu.isnap.dataset.AttemptAction.HINT_DIALOG_LOG_FEEDBACK;
import static edu.isnap.dataset.AttemptAction.SHOW_HINT_MESSAGES;

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
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import distance.RTED_InfoTree_Opt;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.VectorHint;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.dataset.Grade;
import edu.isnap.datasets.Fall2015;
import edu.isnap.datasets.Fall2018;
import edu.isnap.eval.AutoGrader;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.eval.util.Prune;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.util.Spreadsheet;
import util.LblTree;

public class CheckHintUsage {

	private static final long MIN_DURATON_PS = 5 * 60;
	private static final long MIN_DURATON_WE = 30;

	public static void main(String[] args) throws IOException {
		writeHints(Fall2018.instance);
	}

	private static boolean isValidSubmission(AssignmentAttempt attempt) {
		if (attempt == null || attempt.size() == 0) return false;

		// Also ignore any that are shorted than then minimum duration (5m)
		long duration = attempt.totalActiveTime + attempt.totalIdleTime;
		if (!attempt.hasWorkedExample && duration < MIN_DURATON_PS) return false;
		if (attempt.hasWorkedExample && duration < MIN_DURATON_WE) return false;

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

	private static void writeHints(Assignment assignment, Spreadsheet attemptsSheet,
			Spreadsheet hintsSheet)
			throws FileNotFoundException, IOException {
		System.out.println("Writing: " + assignment);

		// Get all submitted attempts at the assignment
		Map<String, AssignmentAttempt> attempts =
				assignment.loadAllLikelySubmitted(Mode.Use, false, true);

		// Iterate over all submissions
		for (String attemptID : attempts.keySet()) {

			AssignmentAttempt attempt = attempts.get(attemptID);
			Grade grade = attempt.grade;
			if (grade != null && grade.outlier) continue;
			String userID = attempt.userID();
			String userIDShort = userID;
			if (userIDShort != null && userIDShort.length() > 8) {
				userIDShort = userIDShort.substring(
						userIDShort.length() - 8, userIDShort.length());
			}

			int nHints = 0, nDuplicateHints = 0, nThumbsUp = 0,
					nThumbsDown = 0, nHintsFollowed = 0, nHintsCloser = 0;
			boolean hasLogs = true;

			// For any that attempt for which we have no logs, we use an empty assignment
			// attempt, which results in 0 for almost every column, but still includes the grades
			if (!isValidSubmission(attempt)) {
				attempt = new AssignmentAttempt(attemptID, attempt.loggedAssignmentID,
						attempt.grade);
				hasLogs = false;
			}

			Snapshot code = null;
			long lastCodeUpdate = 0;
			HashSet<String> uniqueHints = new HashSet<>();
			String lastHintCode = "";

			int edits = 0;
			for (int i = 0; i < attempt.size(); i++) {
				if (attempt.rows.get(i).snapshot != null) edits++;
			}

			int edit = 0;

			// Iterate through each row of the solution path
			for (int i = 0; i < attempt.size(); i++) {
				AttemptAction row = attempt.rows.get(i);

				// If this row had an update to the code, update it
				if (row.snapshot != null) {
					code = row.snapshot;
					lastCodeUpdate = row.timestamp.getTime();
					edit++;
				}

				// If we haven't seen a snapshot, just skip (this can happen due to prequels cutting
				// off logging or other logging errors)
				if (code == null) continue;

				// Get the student's current code and turn it into a tree
				Node node = SimpleNodeBuilder.toTree(code, true);

				double timePerc = (double)row.currentActiveTime / attempt.totalActiveTime;

				// Check if this action was showing a hint
				String action = row.message;
				if (SHOW_HINT_MESSAGES.contains(action)) {

					// Get the data from this event
					JSONObject data = new JSONObject(row.data);

					// Read the list of nodes that the hint is telling to use for the parent's new
					// children
					JSONArray toArray = data.getJSONArray("to");
					String[] to = toChildArray(toArray);

					JSONArray fromArray;
					if (data.has("from")) {
						fromArray = data.getJSONArray("from");
					} else {
						fromArray = data.getJSONArray("fromList").getJSONArray(0);
					}
					String[] from = toChildArray(fromArray);

					long time = row.timestamp.getTime();

					hintsSheet.newRow();
					hintsSheet.put("dataset", assignment.dataset.getName());
					hintsSheet.put("assignment", assignment.name);
					hintsSheet.put("attemptID", attemptID);
					hintsSheet.put("userID", userID);
					hintsSheet.put("rowID", row.id);
					hintsSheet.put("time", time);
					hintsSheet.put("type", action.replace("SnapDisplay.show", "")
							.replace("Hint", ""));
					hintsSheet.put("editPerc", (double)edit / edits);
					hintsSheet.put("timePerc", timePerc);

					// Find the parent node that this hint affects
					Node parent = findParent(row.message, code, node, data, from);

					// Zombie hints are those that stuck around longer than they should have due to
					// a client-side bug, fixed in b34fdab
					boolean zombieHint = false;

					// The parent shouldn't be null
					if (parent == null) {
						parent = checkForZombieHintParent(attempt, data, from, i);
						if (parent != null) zombieHint = true;
					}

					if (parent == null) {
						// If this code is more than a minute out of date, suppress the error
						// (There were probably logging issues)
						if (time - lastCodeUpdate > 1000 * 60) continue;
						System.out.println(node.prettyPrintWithIDs());
						findParent(row.message, code, node, data, from);
						System.out.println(attempt.id + "/" + row.id + ": " + data);
						System.err.println("Parent shouldn't be null :/");
						continue;
					}

					// And apply the hint to the parent to get an outcome parent node
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
					int nodeChange = hintOutcome.treeSize() - parent.treeSize();

					// Calculate original distance between student's code with the hint
					int originalHintDistance = Alignment.alignCost(parent.getChildArray(), to);

					boolean gotCloser = false;
					boolean followed = false;

					long nextActionTime = time;
					long followTime = time;

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
						if (nextActionTime == time) nextActionTime = nextRow.timestamp.getTime();

						// If we've looked more than n (5) steps in the future, give up
						if (steps > 5) break;

						// Find the same parent node and see if it matches the hint state
						Node nextNode = SimpleNodeBuilder.toTree(nextCode, true);
						Node nextParent = nextNode.searchForNodeWithID(parent.id);
						if (nextParent == null) nextParent = findParent(nextNode, data);
						if (nextParent == null) continue;

						int newDistance = Alignment.alignCost(nextParent.getChildArray(), to);
						if (newDistance < originalHintDistance) gotCloser = true;

						if (Arrays.equals(nextParent.getChildArray(), to)) {
							followTime = nextRow.timestamp.getTime();
							followed = true;
						}
					}

					// We get a simple representation for the hint, that omits some of the hint's
					// data fields
					String message = data.has("message") ? data.getString("message") : null;
					String hintCode = String.join("_", parent.id, message, Arrays.toString(from),
							Arrays.toString(to));
					boolean duplicate = !uniqueHints.add(hintCode);
					boolean repeat = lastHintCode.equals(hintCode);
					lastHintCode = hintCode;

					hintsSheet.put("followed", followed);
					hintsSheet.put("delete", delete);
					hintsSheet.put("change", nodeChange);
					hintsSheet.put("duplicate", duplicate);
					hintsSheet.put("repeat", repeat);
					hintsSheet.put("zombie", zombieHint);

					// Determine how long the hint was kept up before being closed
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
					hintsSheet.put("dismissDone", done);
					hintsSheet.put("duration", duration);

					// Determine how long after seeing the hint the student made their next action
					int pause = (int)(nextActionTime - time) / 1000;
					hintsSheet.put("pause", pause);

					if (followed) {
						int followPause = (int)(followTime - time) / 1000;
						hintsSheet.put("followPause", followPause);
					} else {
						hintsSheet.put("followPause", "");
					}

					hintsSheet.put("hash", hintCode.hashCode());

					hintsSheet.put("codeHash", code.toCode(false).hashCode());
					hintsSheet.put("diff", HintPrinter.hintToString(node, hintOutcome));

					// Don't increase cumulative stats for zombie hints
					if (zombieHint) continue;

					nHints++;
					if (duplicate) nDuplicateHints++;
					if (followed) nHintsFollowed++;
					if (gotCloser) nHintsCloser++;
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
			}

			attemptsSheet.newRow();
			// IDs
			attemptsSheet.put("dataset", assignment.dataset.getName());
			attemptsSheet.put("assignment", assignment.name);
			attemptsSheet.put("id", attemptID);
			attemptsSheet.put("userID", userID);
			attemptsSheet.put("logs", hasLogs);
			attemptsSheet.put("isWE", attempt.hasWorkedExample);
			// Hint stats
			attemptsSheet.put("hints", nHints);
			attemptsSheet.put("duplicates", nDuplicateHints);
			attemptsSheet.put("followed", nHintsFollowed);
			attemptsSheet.put("closer", nHintsCloser);
			// Time stats
			attemptsSheet.put("active", attempt.totalActiveTime);
			attemptsSheet.put("idle", attempt.totalIdleTime);
			attemptsSheet.put("total", attempt.totalActiveTime + attempt.totalIdleTime);
			attemptsSheet.put("segments", attempt.timeSegments);

			// Other stats
			attemptsSheet.put("thumbsUp", nThumbsUp);
			attemptsSheet.put("thumbsDown", nThumbsDown);

			if (grade != null) {
				int i = 0;
				int score = 0;
				attemptsSheet.put("grade", grade.average());
				for (Entry<String, Integer> entry : grade.tests.entrySet()) {
					score += entry.getValue();
					attemptsSheet.put("Goal" + i++, entry.getValue());
				}
				attemptsSheet.put("gradePC", score / 2.0 / i);
			}
		}
	}

	protected static String[] toChildArray(JSONArray jsonArray) {
		// Remove the prototypeHatBlock blocks from the array so they match Nodes
		if (jsonArray.length() > 0 &&
				jsonArray.getString(0).equals("prototypeHatBlock")) {
			jsonArray.remove(0);
		}
		String[] to = new String[jsonArray.length()];
		for (int j = 0; j < to.length; j++) {
			to[j] = jsonArray.getString(j)
					// We changed the name, so make the hint match the parser
					.replace("doCustomBlock", "evaluateCustomBlock");
		}
		return to;
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
	 * Zombie hints are those that stuck around longer than they should have due to a client-side
	 * bug, fixed in b34fdab. Due to the zombie hint bug, if the parent is null, we can look back
	 * through previous snapshots for a version of the code compatible with
	 * this hint and use that node are the parent. Note that this zombie hint
	 * doesn't make sense for traditional analysis, since it may not match
	 * the student's current code
	 * @return The correct parent from a previous node, if present, or null if not.
	 */
	public static Node checkForZombieHintParent(AssignmentAttempt attempt, JSONObject data,
			String[] from, int row) {

		for (int j = 1; j <= row; j++) {
			Snapshot previous = attempt.rows.get(row - j).snapshot;
			if (previous == null) continue;
			Node potentialParent =
					findParent(SimpleNodeBuilder.toTree(previous, true), data);
			if (potentialParent != null) {
				if (Arrays.equals(potentialParent.getChildArray(), from)) {
					return potentialParent;
				}
			}
		}

		return null;
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
			JSONObject data, String[] from) {
		Node parent = findParent(root, data);
		if (parent != null) return parent;

		// Hack for custom block structure hints that failed to log rootTypes
		// TODO: does this work for multiple editing blocks?
		if ("SnapDisplay.showStructureHint".equals(message)) {
			if (snapshot.editing.size() >= 1) {
				return root.searchForNodeWithID(snapshot.editing.get(0).getID());
			}
		}

		// There was a logging bug where any hints for the immediate children of a custom block
		// script did not properly record a rootID (it used to be included in the exported XML but
		// is not longer. This should be fixed after Fall 2018, but for not we can only try to
		// guess which custom block the hint referred to.
		if (data.optString("parentSelector").isEmpty() && data.optString("parentID").isEmpty() &&
				data.has("rootID")) {
			String rootID = String.valueOf(data.get("rootID"));
			if (root.searchForNodeWithID(rootID) == null) {
				Node.Predicate pred = new Node.BackbonePredicate("customBlock", "script");
				List<Node> matches = root.searchAll(pred).stream()
					.filter(script -> Arrays.equals(from, script.getChildArray()))
					.collect(Collectors.toList());
				if (matches.size() == 1) {
					return matches.get(0);
				} else if (matches.size() > 1) {
					System.err.println("Multiple matches in missing parent :(");
				} else {
					System.err.println("No matching parents...");
				}
			}
		}

		return null;

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
				if (id.equals("rootType")) {
					parent = root.searchForNodeWithType(parentID);
				} else {
					parent = root.searchForNodeWithID(parentID);
				}
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
