package edu.isnap.ctd.graph;

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
		JSONObject json = new JSONObject(jsonSource);
		boolean isCorrect = json.optBoolean("isCorrect");
		String source = json.optString("source");
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
