package edu.isnap.dataset;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store;
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

	public Assignment(Dataset dataset, String name, Date end, boolean hasIDs) {
		this(dataset, name, end, hasIDs, false, null);
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
			boolean addMetadata) {
		return new SnapParser(this, mode).parseAssignment(snapshotsOnly, addMetadata);
	}

	public final static String BASE_DIR = "../data/csc200";

	// Note: end dates are generally 2 days past last class due date
	public static Date date(int year, int month, int day) {
		// NOTE: GregorianCalendar months are 0-based, thus the 'month - 1'
		return new GregorianCalendar(year, month - 1, day).getTime();
	}
}
