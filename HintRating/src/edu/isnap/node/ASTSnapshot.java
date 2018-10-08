package edu.isnap.node;

import org.json.JSONObject;

public class ASTSnapshot extends ASTNode {

	/**
	 * Will be true if this snapshot represents correct code for the given assignment.
	 * This may also be set to false even for some correct code, for example if there are no unit
	 * tests available for the assignment to determine correctness automatically.
	 */
	public final boolean isCorrect;
	/**
	 * The source code that this snapshot was derived from, if available.
	 */
	public final String source;

	public ASTSnapshot(String type, String value, String id, boolean isCorrect, String sourceCode) {
		super(type, value, id);
		this.isCorrect = isCorrect;
		this.source = sourceCode;
	}

	public static ASTSnapshot parse(String jsonSource) {
		return parse(new JSONObject(jsonSource));
	}

	public static ASTSnapshot parse(JSONObject json) {
		return parse(json, json.optString("source"));
	}

	public static ASTSnapshot parse(JSONObject json, String sourceOverride) {
		// Older versions used "correct"
		boolean isCorrect = json.optBoolean("isCorrect") || json.optBoolean("correct");
		String source = sourceOverride;
		ASTNode node = ASTNode.parse(json);
		return node.toSnapshot(isCorrect, source);
	}

	@Override
	public JSONObject toJSON() {
		JSONObject json = super.toJSON();
		json.put("isCorrect", isCorrect);
		json.put("source", source);
		return json;
	}

}
