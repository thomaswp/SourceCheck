package edu.isnap.ctd.graph;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class ASTNode {
	public String type, value;

	private ASTNode parent;
	private final Map<String, ASTNode> childMap = new LinkedHashMap<>();

	public ASTNode parent() {
		return parent;
	}

	public List<ASTNode> children() {
		return (List<ASTNode>) childMap.values();
	}

	public Map<String, ASTNode> childMap() {
		return Collections.unmodifiableMap(childMap);
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
		int i = childMap.size();
		while (childMap.containsKey(String.valueOf(i))) i++;
		addChild(String.valueOf(i), child);
	}

	public boolean addChild(String relation, ASTNode child) {
		if (childMap.containsKey(relation)) return false;
		childMap.put(relation, child);
		child.parent = this;
		return true;
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
		if (childMap.size() > 0) {
			JSONObject children = new JSONObject();
			for (String relation : childMap.keySet()) {
				children.put(relation, childMap.get(relation).toJSON());
			}
			object.put("children", children);
		}
		return object;
	}

	public Node toNode() {
		return toNode(null);
	}

	public Node toNode(Node parent) {
		Node node = new Node(parent, type);
		node.tag = this;
		for (ASTNode child : childMap.values()) node.children.add(child.toNode(node));
		return node;
	}
}
