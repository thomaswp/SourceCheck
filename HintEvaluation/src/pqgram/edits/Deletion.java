package pqgram.edits;

import astrecognition.model.Graph;

public class Deletion extends PositionalEdit {
	
	private static String DELETION_STRING = "%d: Delete %s from %s (%d)";

	public Deletion(String a, String b, Graph aG, Graph bG, int start) {
		super(a, b, aG, bG, start);
	}

	@Override
	public String toString() {
		return String.format(DELETION_STRING, this.lineNumber, this.b, this.a, this.start);
	}
}
