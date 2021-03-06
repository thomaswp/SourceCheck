package edu.isnap.dataset;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import edu.isnap.parser.SnapParser;
import edu.isnap.parser.elements.Snapshot;

public class AssignmentAttempt implements Iterable<AttemptAction> {

	public final static int UNKNOWN = -1;
	public final static int NOT_SUBMITTED = -2;

	public final String id;
	public final ActionRows rows = new ActionRows();
	/** The primary assignmentID of the logs (e.g. when the attempt was submitted). */
	public final String loggedAssignmentID;
	public final Grade researcherGrade;
	public final Optional<Double> classGrade;
	/** Whether the attempt was ever exported from Snap. */
	public boolean exported;
	/** The Snapshot which was actually submitted, if known. */
	public Snapshot submittedSnapshot;
	/** The ID of the logging row in which the submitted snapshot was exported. */
	public int submittedActionID = UNKNOWN;
	/** Total active time (in seconds) the student spent working on the assignment */
	public int totalActiveTime;
	/** Total idle time (in seconds) the student spent working on the assignment */
	public int totalIdleTime;
	/** The number of work segments, divided by {@link SnapParser#SKIP_DURATION} */
	public int timeSegments;
	/** Should be true if this log belongs to a stand-alone worked example. */
	public boolean hasWorkedExample;

	/** Returns true if this attempt is known not to have been submitted for grading. */
	public boolean isUnsubmitted() {
		return submittedActionID == NOT_SUBMITTED;
	}

	/**
	 * Returns true if this attempt is know to have been submitted for grading or this is unknown,
	 * but it was exported.
	 */
	public boolean isLikelySubmitted() {
		return hasWorkedExample ||
				submittedActionID >= 0 || (submittedActionID == UNKNOWN && exported);
	}

	public boolean isSubmitted() {
		return submittedActionID >= 0;
	}

	@SuppressWarnings("unused")
	private AssignmentAttempt() {
		this(null, null, null, null);
	}

	public AssignmentAttempt(String id, String loggedAssignmentID, Grade grade, Double classGrade) {
		this.id = id;
		this.loggedAssignmentID = loggedAssignmentID;
		this.researcherGrade = grade;
		this.classGrade = Optional.ofNullable(classGrade);
	}

	public void add(AttemptAction row) {
		rows.add(row);
	}

	public int size() {
		return rows.size();
	}

	@Override
	public Iterator<AttemptAction> iterator() {
		return rows.iterator();
	}

	public String userID() {
		return rows.userID;
	}

	@SuppressWarnings("serial")
	public static class ActionRows implements IVersioned, Serializable, Iterable<AttemptAction> {

		/**
		 * Version of the AttemptAction and its stored classes, such as Snapshot.
		 * Change it when they should be reloaded and the cache invalidated.
		 */
		public final static String VERSION = "1.2.0";
		public final List<AttemptAction> rows = new ArrayList<>();

		public String userID;
		private String version = VERSION;

		@Override
		public boolean isUpToDate() {
			return version.equals(VERSION);
		}

		// List wrapper methods

		public void add(AttemptAction row) {
			rows.add(row);
		}

		public int size() {
			return rows.size();
		}

		@Override
		public Iterator<AttemptAction> iterator() {
			return rows.iterator();
		}

		public AttemptAction get(int i) {
			return rows.get(i);
		}

		public AttemptAction getFirst() {
			return rows.get(0);
		}

		public AttemptAction getLast() {
			return rows.get(rows.size() - 1);
		}
	}
}
