package edu.isnap.parser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AssignmentAttempt.ActionRows;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Grade;
import edu.isnap.parser.ParseSubmitted.Submission;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.BlockDefinition;
import edu.isnap.parser.elements.BlockDefinitionGroup.BlockIndex;
import edu.isnap.parser.elements.Snapshot;

/**
 * Parser for iSnap logs.
 */
public class SnapParser {

	/** Number of seconds that pass without an action before the student is considered idle */
	public final static int IDLE_DURATION = 60;
	/** Number of seconds that pass without an action before the student is considered not working*/
	public final static int SKIP_DURATION = 60 * 5;


	/**
	 * Removes all cached files at the given path.
	 * @param path
	 */
	public static void clean(String path) {
		if (!new File(path).exists()) {
			return;
		}
		for (String file : new File(path).list()) {
			File f = new File(path, file);
			if (f.isDirectory()) {
				clean(f.getAbsolutePath());
			} else if (f.getName().endsWith(".cached")) {
				f.delete();
			}
		}
	}

	private final Assignment assignment;
	private final Mode storeMode;
	private final boolean onlyLogExportedCode;

	/**
	 * Constructor for a SnapParser. This should rarely be called directly. Instead, use
	 * {@link Assignment#load()}.
	 *
	 * @param assignment The assignment to load
	 * @param cacheUse The cache {@link Mode} to use when loading data.
	 * @param onlyLogExportedCode
	 */
	public SnapParser(Assignment assignment, Mode cacheUse, boolean onlyLogExportedCode) {
		this.assignment = assignment;
		this.storeMode = cacheUse;
		this.onlyLogExportedCode = onlyLogExportedCode;
		new File(assignment.dataDir).mkdirs();
	}

	public AssignmentAttempt parseSubmission(String id, boolean snapshotsOnly) throws IOException {
		return parseRows(new AttemptParams(id,
				assignment.parsedDir() + File.separator + id + ".csv",
				assignment.name, false, snapshotsOnly, onlyLogExportedCode));
	}

	private String getLogFilePath(String assignmentID, String attemptID) {
		return String.format("%s/parsed/%s/%s.csv",
				assignment.dataDir, assignmentID, attemptID);
	}

	static class RowBuilder {
		public ActionRows solution;
		public String projectID;

		private BlockIndex editingIndex = null;
		private Snapshot lastSnaphot = null;
		private boolean exportedUserID = false;

		// Add variables for missing

		public RowBuilder(String projectID) {
			solution = new ActionRows();
			this.projectID = projectID;
		}

		public void addRow(String action, String data, String userID, String session,
				String xml, int id, String timestampString) {

			Date timestamp = null;
			DateFormat format = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
			try {
				timestamp = format.parse(timestampString);
			} catch (ParseException e) {
				e.printStackTrace();
			}

			// For the help-seeking study the userID was stored in the Logger.started
			// row, rather than as a separate column
			if (action.equals(AttemptAction.IDE_OPENED) && data.length() > 2) {
				JSONObject dataObject = new JSONObject(data);
				if (dataObject.has("userID")) {
					userID = dataObject.getString("userID");
				}
			}
			if (solution.userID == null) {
				solution.userID = userID;
			}

			if (AttemptAction.IDE_EXPORT_PROJECT.equals(action)) {
				if (userID != null && !userID.equals("none")) {
					if (solution.userID == null || !exportedUserID) {
						solution.userID = userID;
						exportedUserID = true;
					} else if (!solution.userID.equals(userID)) {
						// TODO: Make this impossible by implementing the TODOs here and in
						// LogSplitter
						throw new RuntimeException(
								String.format(
										"Project %s exported under multiple userIDs: %s and %s",
										projectID, solution.userID, userID));
					}
				}
			}

			AttemptAction row = new AttemptAction(
					id, projectID, timestamp, session, action, data, xml);

			if (row.snapshot != null) {
				lastSnaphot = row.snapshot;
			}

			// Before BlockDefinitions had GUIDs, they weren't easily identifiable. This
			// is a 99% accurate work-around that stores the index of a the custom
			// block when editing starts and then sets the editing block's index to that
			// index until the editor is closed. Note that we store the index at the
			// start because the spec/type/category may change when editing. Note also
			// that before GUIDs only one block could be edited at a time, so there
			// should only ever be one editing index at a time. If GUIDs are present
			// this does nothing.
			if (AttemptAction.BLOCK_EDITOR_START.equals(action)) {
				JSONObject json = new JSONObject(data);
				String name = json.getString("spec");
				String type = json.getString("type");
				String category = json.getString("category");
				editingIndex = lastSnaphot.getEditingIndex(name, type, category);
				if (editingIndex == null) {
					System.err.println("Edit index not found");
				}
			} else if (AttemptAction.BLOCK_EDITOR_OK.equals(action)) {
				editingIndex = null;
			}
			if (row.snapshot != null) {
				if (row.snapshot.editing.size() == 0) {
					editingIndex = null;
				} else if (row.snapshot.editing.size() == 1) {
					BlockDefinition editing = row.snapshot.editing.get(0);
					if (editing.guid == null) {
						// Only set the blockIndex if we're not using GUIDs
						editing.blockIndex = editingIndex;
					}
				}
			}

			solution.add(row);
		}

		public ActionRows finish() {

			Collections.sort(solution.rows);
			return solution;
		}
	}

	private ActionRows parseActions(final File logFile) {
		final String attemptID = logFile.getName().replace(".csv", "");
		String cachePath = logFile.getAbsolutePath().replace(".csv", ".cached");
		return Store.getCachedObject(new Kryo(), cachePath, ActionRows.class, storeMode,
				new Store.Loader<ActionRows>() {
			@Override
			public ActionRows load() {
				RowBuilder builder = new RowBuilder(attemptID);

				try {
					CSVParser parser = new CSVParser(new FileReader(logFile),
							CSVFormat.EXCEL.withHeader());

					// Backwards compatibility from when we used to call it jsonData
					String dataKey = "data";
					if (!parser.getHeaderMap().containsKey(dataKey)) {
						dataKey = "jsonData";
					}

					boolean hasUserIDs = parser.getHeaderMap().containsKey("userID");
					for (CSVRecord record : parser) {
						String timestampString = record.get("time");

						String action = record.get("message");
						String data = record.get(dataKey);
						String userID = hasUserIDs ? record.get("userID") : null;
						String session = record.get("sessionID");
						String xml = record.get("code");
						String idS = record.get("id");
						int id = -1;
						try {
							id = Integer.parseInt(idS);
						} catch (NumberFormatException e) {
						}

						// For the help-seeking study the userID was stored in the Logger.started
						// row, rather than as a separate column
						if (action.equals(AttemptAction.IDE_OPENED) && data.length() > 2) {
							JSONObject dataObject = new JSONObject(data);
							if (dataObject.has("userID")) {
								userID = dataObject.getString("userID");
							}
						}

						builder.addRow(action, data, userID, session, xml, id, timestampString);

					}

					parser.close();
					System.out.println("Parsed: " + logFile.getName());
				} catch (Exception e) {
					e.printStackTrace();
				}

				return builder.finish();
			}
		});
	}

	private static JSONObject loadRepairedHints(String attemptID, Assignment assignment) {
		if (assignment == null) return null;
		File file = new File(assignment.hintRepairDir() + "/" + attemptID + ".json");
		if (!file.exists()) {
			return null;
		}
		try {
			String content = new String(Files.readAllBytes(file.toPath()));
			return new JSONObject(content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private ActionRows getAllActionRows(AttemptParams params) {
		String attemptID = params.id;

		// First, get all action rows for the attempt
		ActionRows actions = parseActions(new File(params.logPath));
		actions.forEach(action -> action.loggedAssignmentID = params.loggedAssignmentID);

		// Then, if the project started under another assigmentID which was manually identified,
		// add those rows to the beginning
		if (params.startAssignment != null && actions.size() > 0 && params.startID != null) {
			ActionRows startActions =
					parseActions(new File(getLogFilePath(params.startAssignment, attemptID)));
			int startID = actions.rows.stream()
					.filter(action -> action.id >= params.startID)
					.findFirst().get().id;
			startActions.rows.removeIf(action -> action.id < params.startID && action.id > startID);
			startActions.forEach(action -> action.loggedAssignmentID = params.startAssignment);
			actions.rows.addAll(0, startActions.rows);
		}

		// Last, if the project switched between assignments and then came back before submission,
		// add the rows that occurred in between
		for (int i = 0; i < actions.size() - 1; i++) {
			AttemptAction actionSet = actions.get(i);
			if (!AttemptAction.ASSIGNMENT_SET_ID.equals(actionSet.message)) {
				continue;
			}
			String assignmentID = actionSet.data;
			if (assignmentID == null || assignmentID.length() <= 2) {
				continue;
			}
			// At some point, this may need to be less specific, but it seems for now they're always
			// back to back
			AttemptAction actionSetFrom = actions.get(i + 1);
			if (!AttemptAction.ASSIGNMENT_SET_ID_FROM.equals(actionSetFrom.message)) {
				continue;
			}
			if (!actionSetFrom.data.equals(assignmentID)) {
				continue;
			}
			// Trim the quotes
			assignmentID = assignmentID.substring(1, assignmentID.length() - 1);
			ActionRows interlude = parseActions(new File(getLogFilePath(assignmentID, attemptID)));
			// Keep only the rows that come in between the set and setFrom actions
			interlude.rows.removeIf(
					action -> action.id < actionSet.id || action.id > actionSetFrom.id);
			for (AttemptAction action : interlude) {
				action.loggedAssignmentID = assignmentID;
			}
			actions.rows.addAll(i + 1, interlude.rows);
			i += interlude.size();
		}

		// Resort, in case the items we've added were not in time order
		Collections.sort(actions.rows);

		return actions;
	}

	private AssignmentAttempt parseRows(AttemptParams params) {
		ActionRows actions = getAllActionRows(params);
		return parseRows(params, actions, assignment);
	}

	static AssignmentAttempt parseRows(AttemptParams params, ActionRows actions,
			Assignment assignment) {
		String attemptID = params.id;
		Date minDate = null, maxDate = null;
		if (assignment != null) {
			minDate = assignment.start;
			maxDate = assignment.end;
		}

		AssignmentAttempt attempt = new AssignmentAttempt(attemptID, params.loggedAssignmentID,
				params.grade);
		attempt.rows.userID = actions.userID;
		attempt.submittedActionID = params.knownSubmissions ?
				AssignmentAttempt.NOT_SUBMITTED : AssignmentAttempt.UNKNOWN;
		List<AttemptAction> currentWork = new ArrayList<>();

		int gradedRow = params.grade == null ? -1 : params.grade.gradedRow;
		boolean foundGraded = false;
		Snapshot lastSnaphot = null;

		JSONObject repairedHints = null;
		if (params.addMetadata) {
			repairedHints = loadRepairedHints(attemptID, assignment);
		}

		int activeTime = 0;
		int idleTime = 0;
		int workSegments = 0;
		long lastTime = 0;

		for (int i = 0; i < actions.size(); i++) {
			AttemptAction action = actions.get(i);
			if (AttemptAction.WE_START.equals(action.message)) {
				attempt.hasWorkedExample = true;
			}

			// Ignore actions outside of our time range
			if (params.addMetadata && action.timestamp != null && (
					(minDate != null && action.timestamp.before(minDate)) ||
					// Only check max date if we don't have a final submission ID (late work is
					// ok if we can verify it was submitted)
					(params.submittedActionID == null &&
					maxDate != null && action.timestamp.after(maxDate)))) {
				continue;
			}
			// If we're using log data from a prequel assignment, ignore rows before the prequel was
			// submitted
			if (params.prequelEndID != null && action.id <= params.prequelEndID) {
				continue;
			}
			// If we have a start ID, ignore rows that come before it
			if (params.startID != null && action.id < params.startID) {
				continue;
			}

			// In Spring 2017 there was a logging error that failed to log processed hints
			// The are recreated by the HighlightDataRepairer and stored as .json files, which
			// can be loaded and re-inserted into the data.
			if (repairedHints != null && AttemptAction.HINT_PROCESS_HINTS.equals(action.message)) {
				String key = String.valueOf(action.id);
				if (repairedHints.has(key)) {
					String data = repairedHints.getJSONArray(key).toString();
					action = new AttemptAction(action.id, action.timestamp, action.sessionID,
							action.message, data, action.snapshot);
				}
			}

			// Update time statistics, regardless of the action being taken
			{
				long time = action.timestamp.getTime() / 1000;

				if (lastTime == 0) {
					lastTime = time;
					workSegments++;
				} else {
					int duration = (int) (time - lastTime);
					if (duration < SKIP_DURATION) {
						int idleDuration = Math.max(duration - IDLE_DURATION, 0);
						activeTime += duration - idleDuration;
						idleTime += idleDuration;
					} else {
						workSegments++;
					}

					lastTime = time;
					action.currentActiveTime = activeTime;
				}
			}

			// If we're only concerned with snapshots, and this was a Block.grabbed action,
			// we skip it if the next action (presumably a Block.snapped) produces a snapshot
			// as well. This smoothes out some of the quick delete/insert pairs into "moved" events.
			if (params.snapshotsOnly && action.snapshot != null &&
					AttemptAction.BLOCK_GRABBED.equals(action.message)) {
				if (i + 1 < actions.size() && actions.get(i + 1).snapshot != null) {
					continue;
				}

			}

			if (action.snapshot != null) {
				lastSnaphot = action.snapshot;
			}
			action.lastSnapshot = lastSnaphot;

			// Add this row unless it has not snapshot and we want snapshots only
			boolean addRow = !(params.snapshotsOnly && action.snapshot == null);

			if (params.addMetadata) {
				if (addRow) {
					currentWork.add(action);
				}

				// The graded ID is ideally the submitted ID, so this should be redundant
				if (action.id == gradedRow) {
					foundGraded = true;
				}

				boolean done = false;
				boolean saveWork = !params.onlyLogExportedCode;

				// If this is an export keep the work we've seen so far
				if (AttemptAction.IDE_EXPORT_PROJECT.equals(action.message)) {
					attempt.exported = true;
					saveWork = true;
					done |= foundGraded;
				}

				// If this is the submitted action, store that information and finish
				if (params.submittedActionID != null && action.id == params.submittedActionID) {
					attempt.submittedSnapshot = lastSnaphot;
					attempt.submittedActionID = action.id;
					// The attempt must have been exported if we're seeing it, and exported should
					// always be true if submitted is true
					attempt.exported = true;
					done = true;
				}

				if (done || saveWork) {
					// Add the work we've seen so far to the attempt;
					attempt.rows.rows.addAll(currentWork);
					currentWork.clear();
				}
				if (done) {
					break;
				}
			} else if (addRow) {
				attempt.rows.add(action);
			}
		}

		if (params.addMetadata) {
			if (gradedRow >= 0 && !foundGraded) {
				System.err.println("No grade row for: " + params.id);
			}

			// If the solution was exported and submitted, but the log data does not contain
			// the submitted snapshot, check to see what's wrong manually
			if (params.submittedActionID != null && attempt.submittedSnapshot == null) {
				if (attempt.size() > 0) {
					System.out.println(attemptID + ": " + attempt.rows.getLast().id);
				} else {
					System.out.println((assignment == null ? "NONE" : assignment.name) + " " +
							attemptID + ": 0 / " + actions.size() + " / " + params.prequelEndID);
				}
				System.err.printf("Submitted id not found for %s: %s\n",
						attemptID, params.submittedActionID);
			}
		}

		attempt.totalActiveTime = activeTime;
		attempt.totalIdleTime = idleTime;
		attempt.timeSegments = workSegments;

		return attempt;
	}

	/**
	 * Parses the attempts for a given assignment and returns them as a map of id-attempt pairs.
	 * @param snapshotsOnly Whether to include only {@link AttemptAction}s with a snapshot, or all
	 * actions.
	 * @param addMetadata Whether to add metadata, such as start and end dates, grades, submitted
	 * IDs, etc., and additionally filter on this data (e.g. include only actions within the date
	 * range).
	 *
	 * @return
	 */
	// TODO: This should be a different data structure, either a List or a custom class
	public Map<String, AssignmentAttempt> parseAssignment(boolean snapshotsOnly,
			boolean addMetadata, Filter... filters) {
		Map<String, AttemptParams> paramsMap = new TreeMap<>();

		File assignmentDir = new File(assignment.parsedDir());
		if (!assignmentDir.exists()) {
			throw new RuntimeException("Assignment has not been added and parsed: " + assignment);
		}

		for (File file : assignmentDir.listFiles()) {
			if (!file.getName().endsWith(".csv")) {
				continue;
			}
			// TODO: Parse the file name to get the projectID (before the _)
			// Do the same throughout this class
			String attemptID = file.getName().replace(".csv", "");
			String path = getLogFilePath(assignment.name, attemptID);
			AttemptParams params = new AttemptParams(attemptID, path, assignment.name, addMetadata,
					snapshotsOnly, onlyLogExportedCode);
			paramsMap.put(attemptID, params);
		}

		if (addMetadata) {
			// Must come first, since it adds attempts that may have grades/startIDs
			addSubmissionInfo(snapshotsOnly, addMetadata, paramsMap);
			addGrades(paramsMap);
			addStartIDs(paramsMap);
		}

		Map<String, AssignmentAttempt> attempts = new TreeMap<>();
		ExecutorService executor = Executors.newFixedThreadPool(8);
		for (String attemptID : paramsMap.keySet()) {
			AttemptParams params = paramsMap.get(attemptID);

			if (assignment.ignore(attemptID)) {
				continue;
			}
			// TODO: Need to check that all attempts without a grade are really outliers
			if (params.grade != null && params.grade.outlier) {
				continue;
			}
			// Allow filters to skip over attempts before parsing them
			if (Arrays.stream(filters).anyMatch(filter -> filter.skip(params))) {
				continue;
			}

			File file = new File(params.logPath);
			if (!file.exists()) {
				throw new RuntimeException("Missing submission data: " + file.getPath());
			}
			executor.submit(() -> {
				AssignmentAttempt attempt = parseRows(params);
				if (!Arrays.stream(filters).allMatch(filter -> filter.keep(attempt))) {
					return;
				}
				if (attempt.size() <= 3) {
					return;
				}
				synchronized (attempts) {
					attempts.put(attemptID, attempt);
				}
			});
		}
		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return attempts;
	}

	private void addSubmissionInfo(boolean snapshotsOnly, boolean addMetadata,
			Map<String, AttemptParams> paramsMap) {
		Map<String, Map<String, Submission>> allSubmissions =
				ParseSubmitted.getAllSubmissions(assignment.dataset);
		Map<String, Submission> submissions = allSubmissions.get(assignment.name);

		if (submissions == null) {
			return;
		}

		for (String attemptID : submissions.keySet()) {
			Integer prequelEndID = null;
			Submission submission = submissions.get(attemptID);
			if (submission.location == null) {
				continue;
			}

			// Loop through all earlier assignments and see if any have the same submission ID.
			for (Assignment prequel : assignment.dataset.all()) {
				if (prequel == assignment) {
					break;
				}
				Map<String, Submission> prequelSubmissions = allSubmissions.get(prequel.name);
				if (prequelSubmissions.containsKey(attemptID)) {
					Submission prequelSubmission = prequelSubmissions.get(attemptID);

					// If this submission was submitted under the correct assignment and
					// the prequel was submitted under a different assignment, there's
					// no reason to expect this submission contains any work on the prequel
					// (and we could get false positives if prequels were later reopened
					// and re-exported)
					if (assignment.name.equals(submission.location) &&
							!assignment.name.equals(prequelSubmission.location)) {
						continue;
					}

					// Otherwise, if there's a submission row, we put (or update) that as the
					// prequel row. It makes no sense to process data that was logged before
					// the previous assignment was submitted.
					Integer prequalSubmittedRowID = prequelSubmission.submittedRowID;
					if (prequalSubmittedRowID != null) {
						if (submission.submittedRowID != null &&
								submission.submittedRowID < prequalSubmittedRowID) {
							System.err.printf(
									"Prequel submitted later. Check for issues: %s.%s %s\n",
									assignment.dataset.getName(), assignment.name,
									attemptID);
							continue;
						}
//						System.out.printf("%s.%s / %s:\n\t%s: %d vs\n\t%s: %d\n",
//								assignment.dataset.getName(), assignment.name, attemptID,
//								submission.location, submission.submittedRowID,
//								prequelSubmission.location,
//								prequelSubmission.submittedRowID);
						prequelEndID = prequalSubmittedRowID;
					}
				}
			}

			// Update the log path with the one specified in the submission file
			String path = getLogFilePath(submission.location, attemptID);

			AttemptParams params = paramsMap.get(attemptID);
			if (params == null) {
				paramsMap.put(attemptID, params = new AttemptParams(
						attemptID, path, submission.location, addMetadata, snapshotsOnly,
						onlyLogExportedCode));
			} else {
				params.logPath = path;
				params.loggedAssignmentID = submission.location;
			};
			params.prequelEndID = prequelEndID;
			params.submittedActionID = submission.submittedRowID;
		}
		paramsMap.values().forEach(params -> params.knownSubmissions = true);
	}

	public void addStartIDs(Map<String, AttemptParams> paramsMap) {
		File file = new File(assignment.dataset.startsFile());
		if (!file.exists()) {
			return;
		}

		try {
			CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withHeader());

			for (CSVRecord record : parser) {
				String assignmentID = record.get("assignment");
				if (!assignmentID.equals(assignment.name)) {
					continue;
				}

				String attemptID = record.get("id");
				String codeStartString = record.get("code");
				if (codeStartString != null && codeStartString.trim().length() > 0) {
					int codeStart = Integer.parseInt(codeStartString);
					AttemptParams params = paramsMap.get(attemptID);
					if (params == null) {
						System.err.println("Missing logs for attempt with start id: " + attemptID);
						continue;
					}
					params.startID = codeStart;
					if (record.isMapped("startAssignment")) {
						String startAssignment = record.get("startAssignment");
						if (startAssignment.length() > 0) {
							params.startAssignment = startAssignment;
						}
					}
				}
			}

			parser.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addGrades(Map<String, AttemptParams> paramsMap) {
		HashMap<String,Grade> grades = parseGrades();
		for (String attemptID : grades.keySet()) {
			if (assignment.ignore(attemptID)) {
				continue;
			}
			Grade grade = grades.get(attemptID);
			AttemptParams params = paramsMap.get(attemptID);
			if (params == null) {
				if (!attemptID.startsWith(ParseSubmitted.NO_LOG_PREFIX)) {
					System.err.println("Missing logs for graded attempt: " + attemptID);
				}
				continue;
			}
			params.grade = grade;
		}
	}

	public HashMap<String, Grade> parseGrades() {
		HashMap<String, Grade> grades = new HashMap<>();

		File file = new File(assignment.gradesFile());
		if (!file.exists()) {
			if (assignment.graded) {
				System.err.println("No grades file for: " + assignment);
			}
			return grades;
		}

		try {
			CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withHeader());
			Map<String, Integer> headerMap = parser.getHeaderMap();
			String[] header = new String[headerMap.size()];
			for (Entry<String, Integer> entry : headerMap.entrySet()) {
				header[entry.getValue()] = entry.getKey();
			}
			for (CSVRecord record : parser) {
				if (record.get(0).isEmpty()) {
					break;
				}
				Grade grade = new Grade(record, header);
				grades.put(grade.id, grade);
			}
			parser.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return grades;
	}

	public interface Filter {
		public default boolean skip(AttemptParams params) {
			return false;
		}
		public boolean keep(AssignmentAttempt attempt);
	}

	public static class SubmittedOnly implements Filter {
		@Override
		public boolean skip(AttemptParams params) {
			return params.submittedActionID == null;
		}

		@Override
		public boolean keep(AssignmentAttempt attempt) {
			return attempt.isSubmitted();
		}
	}

	public static class LikelySubmittedOnly implements Filter {
		@Override
		public boolean keep(AssignmentAttempt attempt) {
			return attempt.isLikelySubmitted();
		}
	}

	public static class StartedAfter implements Filter {
		private final Date after;

		public StartedAfter(Date after) {
			this.after = after;
		}

		@Override
		public boolean keep(AssignmentAttempt attempt) {
			return attempt.rows.size() > 0 && attempt.rows.get(0).timestamp.after(after);
		}
	}

	public static class AttemptParams {

		public final String id;
		public final boolean addMetadata;
		public final boolean snapshotsOnly;
		public final boolean onlyLogExportedCode;

		public String logPath;
		public String loggedAssignmentID;
		public Grade grade;
		public Integer startID;
		public String startAssignment;
		public boolean knownSubmissions;
		public Integer submittedActionID;
		public Integer prequelEndID;

		public AttemptParams(String id, String logPath, String loggedAssignmentID,
				boolean addMetadata, boolean snapshotsOnly, boolean onlyLogExportedCode) {
			this.id = id;
			this.logPath = logPath;
			this.loggedAssignmentID = loggedAssignmentID;
			this.addMetadata = addMetadata;
			this.snapshotsOnly = snapshotsOnly;
			this.onlyLogExportedCode = onlyLogExportedCode;
		}

	}
}




