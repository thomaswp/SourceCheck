package edu.isnap.ctd.hint;

public class TextHint {

	public final String text;

	@SuppressWarnings("unused")
	private TextHint() { this(null); }

	public TextHint(String text) {
		if (text.startsWith(":")) text = text.substring(1);
		this.text = text;
	}

	public TextHint copy() {
		return new TextHint(text);
	}

	@Override
	public String toString() {
		return "TH: " + text;
	}

}
