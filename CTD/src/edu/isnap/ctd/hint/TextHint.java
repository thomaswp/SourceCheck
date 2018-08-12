package edu.isnap.ctd.hint;

import org.json.JSONObject;

public class TextHint {

	public final String text;
	public final int priority;

	@SuppressWarnings("unused")
	private TextHint() { this(null, 0); }

	public TextHint(String text, int priority) {
		if (text != null && text.startsWith(":")) text = text.substring(1);
		this.text = text;
		this.priority = priority;
	}

	public TextHint copy() {
		return new TextHint(text, priority);
	}

	@Override
	public String toString() {
		return "TH: " + text;
	}

	public JSONObject toJSON() {
		JSONObject data = new JSONObject();
		data.put("text", text);
		data.put("priority", priority);
		return data;
	}

}
