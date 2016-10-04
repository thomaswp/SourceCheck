package com.snap.parser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.snap.data.BlockDefinitionGroup.BlockIndex;
import com.snap.data.Snapshot;
import com.snap.parser.AssignmentAttempt.ActionRows;
import com.snap.parser.Store.Mode;

/**
 * Parser for iSnap logs.
 */
public class SnapParser {

	public static void main(String[] args) {
		// Reloads and caches assignments for a give dataset.

		// Replace Fall2015 with the dataset to reload.
		for (Assignment assignment : Assignment.Fall2015.All) {
			// Loads the given assignment, overwriting any cached data.
			assignment.load(Mode.Overwrite, true);
		}
	}

	/**
	 * Removes all cached files at the given path.
	 * @param path
	 */
	public static void clean(String path) {
		for (String file : new File(path).list()) {
			File f = new File(path, file);
			if (f.isDirectory()) clean(f.getAbsolutePath());
			else if (f.getName().endsWith(".cached")) f.delete();
		}
	}

	private final Assignment assignment;
	private final Mode storeMode;

	/**
	 * Constructor for a SnapParser. This should rarely be called directly. Instead, use
	 * {@link Assignment#load()}.
	 *
	 * @param assignment The assignment to load
	 * @param cacheUse The cache {@link Mode} to use when loading data.
	 */
	public SnapParser(Assignment assignment, Mode cacheUse){
		this.assignment = assignment;
		this.storeMode = cacheUse;
		new File(assignment.dataDir).mkdirs();
	}

	public AssignmentAttempt parseSubmission(String id, boolean snapshotsOnly) throws IOException {
		return parseRows(new File(assignment.parsedDir(), id + ".csv"),
				null, false, null, snapshotsOnly, true);
	}

	private ActionRows parseActions(final File logFile) {
		final String attemptID = logFile.getName().replace(".csv", "");
		String cachePath = logFile.getAbsolutePath().replace(".csv", ".cached");
		return Store.getCachedObject(new Kryo(), cachePath, ActionRows.class, storeMode,
				new Store.Loader<ActionRows>() {
			@Override
			public ActionRows load() {
				ActionRows solution = new ActionRows();

				try {
					CSVParser parser = new CSVParser(new FileReader(logFile),
							CSVFormat.EXCEL.withHeader());

					DateFormat format = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
					BlockIndex editingIndex = null;
					Snapshot lastSnaphot = null;

					for (CSVRecord record : parser) {
						String timestampString = record.get(1);
						Date timestamp = null;
						try {
							timestamp = format.parse(timestampString);
						} catch (ParseException e) {
							e.printStackTrace();
						}

						String action = record.get(2);
						String data = record.get(3);
						String xml = record.get(8);
						String idS = record.get(0);
						int id = -1;
						try {
							id = Integer.parseInt(idS);
						} catch (NumberFormatException e) { }

						AttemptAction row = new AttemptAction(id, attemptID, timestamp, action,
								data, xml);
						if (row.snapshot != null) {
							lastSnaphot = row.snapshot;
						}

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
							if (row.snapshot.editing == null) editingIndex = null;
							else if (row.snapshot.editing.guid == null) {
								row.snapshot.setEditingIndex(editingIndex);
							}
						}

						solution.add(row);

					}
					parser.close();
					System.out.println("Parsed: " + logFile.getName());
				} catch (Exception e) {
					e.printStackTrace();
				}

				Collections.sort(solution);

				return solution;
			}
		});
	}

	private AssignmentAttempt parseRows(File logFile, Grade grade, boolean knownSubmissions,
			Integer submittedActionID, boolean snapshotsOnly, boolean addMetadata)
					throws IOException {

		ActionRows actions = parseActions(logFile);

		String attemptID = logFile.getName().replace(".csv", "");
		Date minDate = assignment.start, maxDate = assignment.end;

		AssignmentAttempt attempt = new AssignmentAttempt(grade);
		attempt.submittedActionID = knownSubmissions ?
				AssignmentAttempt.NOT_SUBMITTED : AssignmentAttempt.UNKNOWN;
		List<AttemptAction> currentWork = new ArrayList<AttemptAction>();

		String gradedID = grade == null ? null : grade.gradedID;
		boolean foundGraded = false;
		Snapshot lastSnaphot = null;

		for (int i = 0; i < actions.size(); i++) {
			AttemptAction action = actions.get(i);

			// Ignore actions outside of our time range
			if (addMetadata && action.timestamp != null && (
					(minDate != null && action.timestamp.before(minDate)) ||
					(maxDate != null && action.timestamp.after(maxDate)))) {
				continue;
			}

			// If we're only concerned with snapshots, and this was a Block.grabbed action,
			// we skip it if the next action (presumably a Block.snapped) produces a snapshot
			// as well. This smooths out some of the quick delete/insert pairs into "moved" events.
			if (snapshotsOnly && action.snapshot != null &&
					AttemptAction.BLOCK_GRABBED.equals(action.message)) {
				if (i + 1 < actions.size() && actions.get(i + 1).snapshot != null) {
					continue;
				}

			}

			// Add this row unless it has not snapshot and we want snapshots only
			boolean addRow = !(snapshotsOnly && action.snapshot == null);

			if (addMetadata) {
				if (addRow) {
					currentWork.add(action);
				}

				// The graded ID is ideally the submitted ID, so this should be redundant
				if (String.valueOf(action.id).equals(gradedID)) {
					foundGraded = true;
				}

				boolean done = false;
				boolean saveWork = false;

				// If this is an export keep the work we've seen so far
				if (AttemptAction.IDE_EXPORT_PROJECT.equals(action)) {
					attempt.exported = true;
					saveWork = true;
					done |= foundGraded;
				}

				if (action.snapshot != null) lastSnaphot = action.snapshot;
				// If this is the submitted action, store that information and finish
				if (submittedActionID != null && action.id == submittedActionID) {
					attempt.submittedSnapshot = lastSnaphot;
					attempt.submittedActionID = action.id;
					done = true;
				}

				if (done || saveWork) {
					// Add the work we've seen so far to the attempt;
					attempt.rows.addAll(currentWork);
					currentWork.clear();
				}
				if (done) {
					break;
				}
			} else if (addRow){
				attempt.rows.add(action);
			}
		}

		if (addMetadata) {
			if (gradedID != null && !foundGraded) {
				System.err.println("No grade row for: " + logFile.getName());
			}

			// If the solution was exported and submitted, but the log data does not contain
			// the submitted snapshot, check to see what's wrong manually
			if (submittedActionID != null && attempt.submittedSnapshot == null) {
				System.err.printf("Submitted id not found for %s: %s\n",
						attemptID, submittedActionID);
			}
		}

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
	public Map<String, AssignmentAttempt> parseAssignment(boolean snapshotsOnly,
			boolean addMetadata) {
		Map<String, AssignmentAttempt> submissions = new TreeMap<String, AssignmentAttempt>();


		HashMap<String, Grade> grades = new HashMap<String, Grade>();
		// TODO: Get submitted assignment location; filter out logs part of prequel assignments
		Map<String, Integer> submittedRows = null;

		if (addMetadata) {
			grades = parseGrades();
			submittedRows = ParseSubmitted.getSubmittedRows(assignment);
		}

		final AtomicInteger threads = new AtomicInteger();
		for (File file : new File(assignment.parsedDir()).listFiles()) {
			parseCSV(file, snapshotsOnly, addMetadata, false, submissions, submittedRows, grades,
					threads);
		}
		while (threads.get() != 0) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return submissions;
	}

	private void parseCSV(File file, final boolean snapshotsOnly, final boolean submittedOnly,
			final boolean addMetadata, final Map<String, AssignmentAttempt> submissions,
			final Map<String, Integer> submittedRows,
			Map<String, Grade> grades, final AtomicInteger threads) {
		if (!file.getName().endsWith(".csv")) return;
		final File fFile = file;
		final String guid = file.getName().replace(".csv", "");
		final Grade grade = grades.get(guid);
		final Integer submittedRow = submittedRows == null ?
				null : submittedRows.get(guid);

		threads.incrementAndGet();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (!assignment.ignore(guid) && (grade == null || !grade.outlier)) {
						AssignmentAttempt attempt = parseRows(fFile, grade,
								submittedRows != null, submittedRow, snapshotsOnly, addMetadata);
						if (attempt.size() > 3 &&
								!(submittedOnly && attempt.submittedSnapshot == null)) {
							synchronized (submissions) {
								submissions.put(guid, attempt);
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				threads.decrementAndGet();
			}
		}).run(); // TODO: Figure out why parallel doesn't work
		// Hint - it's probably because Kryo isn't thread-safe and you use one static instance
	}


	private HashMap<String, Grade> parseGrades() {
		HashMap<String, Grade> grades = new HashMap<String, Grade>();

		File file = new File(assignment.gradesFile());
		if (!file.exists()) return grades;

		try {
			CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withHeader());
			for (CSVRecord record : parser) {
				Grade grade = new Grade(record);
				grades.put(grade.id, grade);
			}
			parser.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return grades;
	}
}




