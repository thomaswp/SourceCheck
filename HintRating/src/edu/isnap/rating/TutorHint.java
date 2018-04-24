package edu.isnap.rating;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;

public class TutorHint {

	public enum Validity {
		NoTutors(0), OneTutor(1), MultipleTutors(2), Consensus(3);

		public final int value;

		Validity(int value) {
			this.value = value;
		}

		public static Validity fromInt(int value) {
			for (Validity validity : Validity.values()) {
				if (validity.value == value) return validity;
			}
			return null;
		}

		public boolean isAtLeast(Validity minValidity) {
			return value >= minValidity.value;
		}
	}

	public enum Priority {
		// TODO: Remove TooSoon for release
		Highest(1), High(2), Normal(3), TooSoon(4);

		public final int value;

		public int points() {
			return 4 - value;
		}

		Priority(int value) {
			this.value = value;
		}

		public static Priority fromInt(int value) {
			for (Priority priority : Priority.values()) {
				if (priority.value == value) return priority;
			}
			return null;
		}
	}

	public final int hintID;
	public final String requestID;
	public final String year;
	public final String tutor, assignmentID;
	public final ASTNode from, to;

	public Validity validity;
	public Priority priority;

	public TutorHint(int hintID, String requestID, String tutor, String assignmentID, String year,
			ASTNode from, ASTNode to) {
		this.hintID = hintID;
		this.requestID = requestID;
		this.tutor = tutor;
		this.assignmentID = assignmentID;
		this.year = year;
		this.from = from;
		this.to = to;
	}

	public String toDiff(RatingConfig config) {
		return Diff.diff(from.prettyPrint(true, config), to.prettyPrint(true, config));
	}

	@Override
	public String toString() {
		return String.format("%s, request %s, hint #%d", tutor, requestID, hintID);
	}

	public HintOutcome toOutcome() {
		return new HintOutcome(to, assignmentID, requestID,
				priority == null ? 1 : (1.0 / priority.value));
	}
}