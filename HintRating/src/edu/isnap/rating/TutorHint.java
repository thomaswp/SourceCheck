package edu.isnap.rating;

import java.util.Comparator;
import java.util.EnumSet;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.Diff;

public class TutorHint implements Comparable<TutorHint> {

	public enum Validity {
		OneTutor(1), MultipleTutors(2), Consensus(3);

		public final int value;

		Validity(int value) {
			this.value = value;
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

	// We use an EnumSet for validity, since they do no have a strict ordering. A hint may have
	// only 1 initial tutor vote but then be included in consensus (so Consensus, but not
	// MultipleTutors).
	public EnumSet<Validity> validity;
	public Priority priority;

	private Validity highestValidity() {
		return validity.stream().max(Comparator.comparing(v -> v.value)).orElseGet(() -> null);
	}

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
				priority == null ? 1 : priority.points());
	}

	private static Comparator<TutorHint> comparator =
			Comparator.comparing((TutorHint hint) -> hint.assignmentID)
			.thenComparing(hint -> hint.year == null ? "" : hint.year)
			.thenComparing(hint -> hint.requestID)
			.thenComparing(hint -> hint.highestValidity())
			.thenComparing(hint -> hint.priority == null ? 0 : hint.priority.value);

	@Override
	public int compareTo(TutorHint o) {
		return comparator.compare(this, o);
	}
}