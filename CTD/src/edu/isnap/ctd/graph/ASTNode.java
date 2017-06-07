package edu.isnap.ctd.graph;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ASTNode {
	public String type, value, relationshipToParent;

	private ASTNode parent;
	private final List<ASTNode> children = new LinkedList<>();

	public ASTNode parent() {
		return parent;
	}

	public List<ASTNode> children() {
		return Collections.unmodifiableList(children);
	}

	public ASTNode(String type) {
		this(type, null);
	}

	public ASTNode(String type, String value) {
		if (type == null) throw new IllegalArgumentException("'type' cannot be null");
		this.type = type;
		this.value = value;
	}

	public void addChild(ASTNode child) {
		addChild(null, child);
	}

	public void addChild(String relationship, ASTNode child) {
		children.add(child);
		child.parent = this;
		child.relationshipToParent = relationship;
	}

	public static ASTNode parse(String jsonSource) throws JSONException {
		JSONObject object = new JSONObject(jsonSource);
		return parse(object);
	}

	public static ASTNode parse(JSONObject object) {
		String type = object.getString("type");
		String value = object.has("value") ? object.getString("value") : null;

		ASTNode node = new ASTNode(type, value);

		JSONObject children = object.optJSONObject("children");
		if (children != null) {
			@SuppressWarnings("unchecked")
			Iterator<String> keys = children.keys();
			while (keys.hasNext()) {
				String relation = keys.next();
				ASTNode child = parse(children.getJSONObject(relation));
				node.addChild(relation, child);
			}
		}

		return node;
	}

	public JSONObject toJSON() {
		JSONObject object = new JSONObject();
		object.put("type", type);
		if (value != null) object.put("value", value);
		if (relationshipToParent != null) object.put("relationshipToParent", relationshipToParent);
		if (children.size() > 0) {
			JSONArray children = new JSONArray();
			for (ASTNode child : this.children) {
				children.put(child.toJSON());
			}
			object.put("children", children);
		}
		return object;
	}
}
