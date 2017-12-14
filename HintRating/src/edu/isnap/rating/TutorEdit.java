package edu.isnap.rating;

import edu.isnap.ctd.graph.ASTNode;

public class TutorEdit {

	public enum Validity {
		NoTutors(0), OneTutor(1), MultipleTutors(2), Consensus(3);

		public final int value;

		Validity(int value) {
			this.value = value;
		}
	}

	public enum Priority {
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
	// TODO: Make this just a string
	public final int requestID;
	public final String requestIDString;
	public final String tutor, assignmentID;
	public final ASTNode from, to;

	public Validity validity;
	public Priority priority;

	public TutorEdit(int hintID, String requestID, String tutor, String assignmentID, ASTNode from,
			ASTNode to) {
		this.hintID = hintID;
		this.requestIDString = requestID;
		this.requestID = parseRequestID(requestID);
		this.tutor = tutor;
		this.assignmentID = assignmentID;
		this.from = from;
		this.to = to;
	}

	private static int parseRequestID(String requestID) {
		try {
			return Integer.parseInt(requestID);
		} catch (NumberFormatException e) { }
		if (requestID.length() > 8) requestID = requestID.substring(0, 8);
		try {
			return Integer.parseInt(requestID, 16);
		} catch (NumberFormatException e) { }
		return requestID.hashCode();
	}

	@Override
	public String toString() {
		return String.format("%s, request %s, hint #%d", tutor, requestIDString, hintID);
	}

	public HintOutcome toOutcome() {
		return new HintOutcome(to, requestID, priority == null ? 0 : (1.0 / priority.value));
	}
}