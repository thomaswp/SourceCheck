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

	private final String rootFolder;
	private final Mode storeMode;

	/**
	 * Constructor for a SnapParser. This should rarely be called directly. Instead, use
	 * {@link Assignment#load()}.
	 *
	 * @param rootFolder The root folder for a dataset from which files will be loaded.
	 * @param cacheUse The cache {@link Mode} to use when loading data.
	 */
	public SnapParser(String rootFolder, Mode cacheUse){
		this.rootFolder = rootFolder;
		this.storeMode = cacheUse;
		new File(rootFolder).mkdirs();
	}

	public AssignmentAttempt parseSubmission(Assignment assignment, String id, boolean snapshotsOnly)
			throws IOException {
		return parseRows(new File(assignment.dataDir + "/parsed/" + assignment.name, id + ".csv"),
				null, snapshotsOnly, assignment.start, assignment.end);
	}

	private AssignmentAttempt parseRows(final File logFile, final Grade grade,
			final boolean snapshotsOnly, final Date minDate, final Date maxDate)
					throws IOException {
		final String attemptID = logFile.getName().replace(".csv", "");
		String cachePath = logFile.getAbsolutePath().replace(".csv", "") +
				(snapshotsOnly ? "" :  "-data");
		int hash = 0;
		if (minDate != null) hash += minDate.hashCode();
		if (maxDate != null) hash += maxDate.hashCode();
		if (hash != 0) cachePath += "-d" + (hash);
		cachePath += ".cached";

		return Store.getCachedObject(new Kryo(), cachePath, AssignmentAttempt.class, storeMode,
				new Store.Loader<AssignmentAttempt>() {
			@Override
			public AssignmentAttempt load() {
				AssignmentAttempt solution = new AssignmentAttempt(grade);
				try {
					CSVParser parser = new CSVParser(new FileReader(logFile),
							CSVFormat.EXCEL.withHeader());

					DateFormat format = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");

					String lastGrab = null;

					String gradedID = grade == null ? null : grade.gradedID;
					boolean foundGraded = false;

					BlockIndex editingIndex = null;
					Snapshot lastSnaphot = null;

					List<AttemptAction> currentWork = new ArrayList<AttemptAction>();

					for (CSVRecord record : parser) {
						String timestampString = record.get(1);
						Date timestamp = null;
						try {
							timestamp = format.parse(timestampString);
						} catch (ParseException e) {
							e.printStackTrace();
						}

						if (timestamp != null && (
								(minDate != null && timestamp.before(minDate)) ||
								(maxDate != null && timestamp.after(maxDate)))) {
							continue;
						}

						String action = record.get(2);
						String data = record.get(3);

						String xml = record.get(8);
						if (snapshotsOnly && action.equals("Block.grabbed")) {
							if (xml.length() > 2) lastGrab = xml;
							continue;
						} else if (xml.length() <= 2 && lastGrab != null) {
							xml = lastGrab;
						}

						String idS = record.get(0);
						int id = -1;
						try {
							id = Integer.parseInt(idS);
						} catch (NumberFormatException e) { }

						AttemptAction row = new AttemptAction(id, attemptID, timestamp, action, data, xml);
						if (row.snapshot != null) lastSnaphot = row.snapshot;

						if ("BlockEditor.start".equals(action)) {
							JSONObject json = new JSONObject(data);
							String name = json.getString("spec");
							String type = json.getString("type");
							String category = json.getString("category");
							editingIndex = lastSnaphot.getEditingIndex(name, type, category);
							if (editingIndex == null) {
								System.err.println("Edit index not found");
							}
						} else if ("BlockEditor.ok".equals(action)) {
							editingIndex = null;
						}
						if (row.snapshot != null) {
							if (row.snapshot.editing == null) editingIndex = null;
							else if (row.snapshot.editing.guid == null) {
								row.snapshot.setEditingIndex(editingIndex);
							}
						}

						if (!snapshotsOnly || row.snapshot != null) {
							currentWork.add(row);
							lastGrab = null;
						}

						if (idS.equals(gradedID)) {
							foundGraded = true;
						}

						if (action.equals("IDE.exportProject")) {
							solution.exported = true;
							for (AttemptAction r : currentWork) {
								solution.add(r);
							}
							currentWork.clear();
							if (foundGraded) break;
						}

					}
					parser.close();

					if (gradedID != null && !foundGraded) {
						System.err.println("No grade row for: " + logFile.getName());
					}

					System.out.println("Parsed: " + logFile.getName());
				} catch (Exception e) {
					e.printStackTrace();
				}

				Collections.sort(solution.rows);

				return solution;
			}
		});
	}

	public Map<String, AssignmentAttempt> parseAssignment(String folder, final boolean snapshotsOnly) {
		return parseAssignment(folder, snapshotsOnly, null, null);
	}

	public Map<String, AssignmentAttempt> parseAssignment(String folder, final boolean snapshotsOnly,
			final Date minDate, final Date maxDate) {
		HashMap<String, Grade> grades = parseGrades(folder);

		final Map<String, AssignmentAttempt> students = new TreeMap<String, AssignmentAttempt>();
		final AtomicInteger threads = new AtomicInteger();
		for (File file : new File(rootFolder + "/" + "parsed", folder).listFiles()) {
			if (!file.getName().endsWith(".csv")) continue;
			final File fFile = file;
			final Grade grade = grades.get(file.getName().replace(".csv", ""));
			threads.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						AssignmentAttempt rows = parseRows(fFile, grade, snapshotsOnly, minDate,
								maxDate);
						if (rows.grade == null || !rows.grade.outlier) {
							if (rows.size() > 3) {
								synchronized (students) {
									students.put(fFile.getName(), rows);
								}
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					threads.decrementAndGet();
				}
			}).run(); // TODO: Figure out why parallel doesn't work
		}
		while (threads.get() != 0) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return students;
	}


	private HashMap<String, Grade> parseGrades(String folder) {
		HashMap<String, Grade> grades = new HashMap<String, Grade>();

		File file = new File(rootFolder, "grades/" + folder + ".csv");
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




