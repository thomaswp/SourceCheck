package edu.isnap.ctd.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.isnap.ctd.graph.PrettyPrint.Params;
import edu.isnap.ctd.util.Diff;
import edu.isnap.rating.RatingConfig;

public class ASTNode implements INode {

	/**
	 * Represents JSON AST nodes that were null, meaning a empty node. Rather than reading these as
	 * null (which could mess up any algorithms operating on the ASTNode), we create a node with the
	 * type held in this constant (and no value or id). This is different than an node actually
	 * representing null/nil/None in the AST, which would presumably have a different type.
	 * We chose not to ignore these nodes completely, since often they are placeholders for other
	 * nodes that could exist but where none currently exists, and the index ordering is meaningful.
	 */
	public final static String EMPTY_TYPE = "null";

	/**
	 * The type of this node, e.g. literal, variable-declaration, binary-operation.
	 */
	public String type;

	/**
	 * The value for this ASTNode, such as the value of a literal, the name of a function
	 * definition, or the name of a variable.
	 */
	public String value;

	/**
	 * An identifier for this ASTNode, which should be unique for this AST (though uniqueness is not
	 * enforced by this class).
	 * Note: the {@link ASTNode#equals(Object)} method does not compare node IDs, since this is
	 * assumed to be the preferred (and more useful) interpretation of equality between ASTNodes,
	 * allowing code from different sources to be considered equal. The
	 * {@link ASTNode#equals(ASTNode, boolean, boolean)} method can be used if this is the desired
	 * behavior.
	 */
	public String id;

	private ASTNode parent;

	private final List<ASTNode> children = new ArrayList<>();
	private final List<ASTNode> unmodifiableChildren = Collections.unmodifiableList(children);

	private final List<String> childRelations = new ArrayList<>();


	@Override
	public ASTNode parent() {
		return parent;
	}

	@Override
	public String type() {
		return type;
	}

	@Override
	public String value() {
		return value;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public List<ASTNode> children() {
		return unmodifiableChildren;
	}

	public ASTNode(String type, String value, String id) {
		if (type == null) throw new IllegalArgumentException("'type' cannot be null");
		this.type = type;
		this.value = value;
		this.id = id;
	}

	public boolean addChild(ASTNode child) {
		return addChild(children.size(), child);
	}

	public boolean addChild(String relation, ASTNode child) {
		return addChild(children.size(), relation, child);
	}

	public boolean addChild(int index, ASTNode child) {
		int i = children.size();
		while (childRelations.contains(String.valueOf(i))) i++;
		return addChild(String.valueOf(i), child);
	}

	public boolean addChild(int index, String relation, ASTNode child) {
		if (childRelations.contains(relation)) return false;
		children.add(index, child);
		childRelations.add(index, relation);
		if (child != null) child.parent = this;
		return true;
	}

	public void removeChild(int index) {
		ASTNode child = children.remove(index);
		childRelations.remove(index);
		child.parent = null;
	}

	public void clearChildren() {
		children.forEach(c -> {
			if (c.parent == this) c.parent = null;
		});
		children.clear();
		childRelations.clear();
	}

	public String prettyPrint(boolean showValues, RatingConfig config) {
		return prettyPrint(showValues, config::nodeTypeHasBody);
	}

	private String prettyPrint(boolean showValues, Predicate<String> isBodyType) {
		Params params = new Params();
		params.showValues = showValues;
		params.isBodyType = isBodyType;
		return PrettyPrint.toString(this, params);
	}

	public static String diff(ASTNode a, ASTNode b, RatingConfig config) {
		return Diff.diff(a.prettyPrint(true, config), b.prettyPrint(true, config));
	}

	public static String diff(ASTNode a, ASTNode b, RatingConfig config, int margin) {
		return Diff.diff(a.prettyPrint(true, config), b.prettyPrint(true, config), margin);
	}

	public static ASTNode parse(String jsonSource) throws JSONException {
		JSONObject object;
		try {
			object = new JSONObject(jsonSource);
		} catch (Exception e) {
			System.out.println("Error parsing JSON:");
			System.out.println(jsonSource);
			throw e;
		}
		return parse(object);
	}

	public static ASTNode parse(JSONObject object) {
		String type = object.getString("type");
		String value = object.has("value") ? object.getString("value") : null;
		String id = object.has("id") ? object.getString("id") : null;

		// TODO: Remove IDs from the data and remove this line!
		if ("script".equals(type)) id = null;

		ASTNode node = new ASTNode(type, value, id);

		JSONObject children = object.optJSONObject("children");
		if (children != null) {
			JSONArray childrenOrder = object.optJSONArray("childrenOrder");

			if (childrenOrder == null) {
				// If we are not explicitly provided an order, just use the internal hash map's keys
				@SuppressWarnings("unchecked")
				Iterator<String> keys = children.keys();
				childrenOrder = new JSONArray();
				while (keys.hasNext()) {
					childrenOrder.put(keys.next());
				}
			}

			for (int i = 0; i < childrenOrder.length(); i++) {
				String relation = childrenOrder.getString(i);
				if (children.isNull(relation)) {
					node.addChild(relation, new ASTNode(EMPTY_TYPE, null, null));
					continue;
				}

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
		if (id != null) object.put("id", id);
		if (children.size() > 0) {
			JSONObject children = new JSONObject();
			JSONArray childrenOrder = new JSONArray();
			for (int i = 0; i < this.children.size(); i++) {
				String relation = childRelations.get(i);
				children.put(relation, this.children.get(i).toJSON());
				childrenOrder.put(relation);
			}
			object.put("children", children);
			object.put("childrenOrder", childrenOrder);
		}
		return object;
	}

	public void autoID(String prefix) {
		autoID(prefix, new AtomicInteger(0));
	}

	private void autoID(String prefix, AtomicInteger id) {
		if (this.id == null) this.id = prefix + id.getAndIncrement();
		for (ASTNode child : children) {
			child.autoID(prefix, id);
		}
	}

	public int depth() {
		if (parent == null) return 0;
		return parent.depth() + 1;
	}

	public ASTNode copy() {
		ASTNode copy = shallowCopy();
		for (int i = 0; i < children.size(); i++) {
			ASTNode child = children.get(i);
			copy.addChild(childRelations.get(i), child == null ? null : child.copy());
		}
		return copy;
	}

	public ASTNode shallowCopy() {
		return new ASTNode(type, value, id);
	}

	public void recurse(Consumer<ASTNode> action) {
		action.accept(this);
		for (ASTNode child : children) {
			if (child != null) child.recurse(action);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj == this) return true;
		if (obj.getClass() != getClass()) return false;
		ASTNode rhs = (ASTNode) obj;
		return equals(rhs, false, false);
	}

	public boolean equals(ASTNode rhs, boolean compareIDs, boolean compareChildRelations) {
		if (!shallowEquals(rhs, compareIDs)) return false;
		if (children.size() != rhs.children.size()) return false;
		// Compare children manually so we can pass on the compare flags
		for (int i = 0; i < children.size(); i++) {
			if (!equals(children.get(i), rhs.children.get(i), compareIDs, compareChildRelations)) {
				return false;
			}
		}
		if (compareChildRelations && !childRelations.equals(rhs.childRelations)) return false;
		return true;
	}

	public static boolean equals(ASTNode a, ASTNode b, boolean compareIDs,
			boolean compareChildRelations) {
		if (a == null) return b == null;
		return a.equals(b, compareIDs, compareChildRelations);
	}

	public boolean shallowEquals(ASTNode rhs, boolean compareIDs) {
		if (rhs == null) return false;
		EqualsBuilder builder = new EqualsBuilder();
		builder.append(type, rhs.type);
		builder.append(value, rhs.value);
		if (compareIDs) builder.append(id, rhs.id);
		return builder.isEquals();
	}

	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder(9, 15);
		builder.append(type);
		builder.append(value);
		builder.append(children);
		return builder.toHashCode();
	}

	@Override
	public String toString() {
		return prettyPrint(false, node -> false);
	}

	public ASTSnapshot toSnapshot() {
		return toSnapshot(false, null);
	}

	public ASTSnapshot toSnapshot(boolean isCorrect, String source) {
		ASTSnapshot snapshot = new ASTSnapshot(type, value, id, isCorrect, source);
		for (int i = 0; i < children.size(); i++) {
			snapshot.addChild(childRelations.get(i), children.get(i));
		}
		return snapshot;
	}
}
