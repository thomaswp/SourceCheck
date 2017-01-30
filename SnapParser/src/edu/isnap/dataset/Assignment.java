package edu.isnap.dataset;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import edu.isnap.parser.ParseSubmitted;
import edu.isnap.parser.ParseSubmitted.Submission;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.SnapParser.Filter;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class Assignment {
	public final Dataset dataset;
	public final String dataDir, name;
	public final Date start, end;
	public final boolean hasIDs;
	public final boolean graded;
	public final Assignment prequel;
	public final Assignment None;

	public Assignment(Dataset dataset, String name, Date end, boolean hasNodeIDs) {
		this(dataset, name, end, hasNodeIDs, false, null);
	}

	public Assignment(Dataset dataset, String name, Date end, boolean hasIDs, boolean graded,
			Assignment prequel) {
		this.dataset = dataset;
		this.dataDir = dataset.dataDir;
		this.name = name;
		this.start = dataset.start;
		this.end = end;
		this.hasIDs = hasIDs;
		this.graded = graded;
		this.prequel = prequel;
		this.None = name.equals("none") ? null : new Assignment(dataset, "none", end, hasIDs);
	}

	public String analysisDir() {
		return dir("analysis");
	}

	public String unitTestDir() {
		return dir("unittests");
	}

	public String submittedDir() {
		return dir("submitted");
	}

	public String gradesFile() {
		return dir("grades") + ".csv";
	}

	public String parsedDir() {
		return dir("parsed");
	}

	public String dir(String folderName) {
		return dataDir + "/" + folderName + "/" + name;
	}

	public void clean() {
		SnapParser.clean(parsedDir());
	}

	public Snapshot loadSolution() throws FileNotFoundException {
		return Snapshot.parse(new File(dataDir + "/solutions/", name + ".xml"));
	}

	public Snapshot loadTest(String name) throws FileNotFoundException {
		return Snapshot.parse(new File(dataDir + "/tests/", name + ".xml"));
	}

	public AssignmentAttempt loadSubmission(String id, Mode mode, boolean snapshotsOnly) {
		try {
			return new SnapParser(this, mode).parseSubmission(id, snapshotsOnly);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	/** Override to ignore certain known bad attempts. */
	public boolean ignore(String attemptID) {
		return false;
	}

	/** Override to mark certain attempts as being submitted under a different assignment folder. */
	public Assignment getLocationAssignment(String attemptID) {
		return this;
	}

	/**
	 * Override to manually define the submitted row ID for attempts that change minimally from that
	 * row to submission.
	 */
	public Integer getSubmittedRow(String attemptID) {
		return null;
	}

	@Override
	public String toString() {
		return dataDir + "/" + name;
	}

	public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly) {
		return load(mode, snapshotsOnly, true);
	}

	public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly,
			boolean addMetadata, Filter... filters) {
		return new SnapParser(this, mode).parseAssignment(snapshotsOnly, addMetadata, filters);
	}

	public final static String CSC200_BASE_DIR = "../data/csc200";

	// Note: end dates are generally 2 days past last class due date
	public static Date date(int year, int month, int day) {
		// NOTE: GregorianCalendar months are 0-based, thus the 'month - 1'
		return new GregorianCalendar(year, month - 1, day).getTime();
	}

	public HashMap<String,Grade> loadGrades() {
		return new SnapParser(this, Mode.Ignore).parseGrades();
	}

	/**
	 * Loads an attempt for every submitted snapshot for this assignment. For submitted attempts
	 * that have no logs, returns an empty AssignmentAttempt.
	 */
	public Map<String, AssignmentAttempt> loadAllSubmitted(Mode mode, boolean snapshotsOnly,
			boolean addMetadata) {
		// Start with all the base set of attempts
		Map<String, AssignmentAttempt> attempts = load(mode, snapshotsOnly, addMetadata,
				new SnapParser.SubmittedOnly());
		// Then add any submission not included in the logs
		Map<String, Submission> submissions = ParseSubmitted.getSubmissions(this);
		HashMap<String, Grade> grades = loadGrades();
		for (String attemptID : submissions.keySet()) {
			// If we don't have an attempt, add a fake one
			if (attempts.containsKey(attemptID)) continue;
			Grade grade = grades.get(attemptID);
			AssignmentAttempt attempt = new AssignmentAttempt(attemptID, grade);
			attempt.exported = grade == null || !grade.outlier;
			attempts.put(attemptID, attempt);
		}
		return attempts;
	}
}
