package edu.isnap.ctd.hint.edit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.Canonicalization;
import edu.isnap.ctd.hint.Canonicalization.SwapBinaryArgs;
import edu.isnap.ctd.hint.Hint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.NodeAlignment.Mapping;

public abstract class EditHint implements Hint, Comparable<EditHint> {
	protected abstract void editChildren(List<String> children);
	protected abstract String action();
	protected abstract double priority();
	protected abstract void appendHashCodeFieds(HashCodeBuilder builder);
	protected abstract void appendEqualsFieds(EqualsBuilder builder, EditHint rhs);
	protected abstract void addApplications(Node root, Node editParent,
			List<Application> applications);
	public abstract Node getPriorityToNode(Mapping mapping);

	public static boolean useValues = true;

	public final Node parent;

	public transient Priority priority;
	protected transient RuntimeException e;

	private final boolean argsCanonSwapped;

	public EditHint(Node parent) {
		this.parent = parent;

		boolean swap = false;
		for (Canonicalization c : parent.canonicalizations) {
			if (c instanceof SwapBinaryArgs) {
				swap = true;
				break;
			}
			// We don't have to worry about Reorder canonicalizations because then the order of
			// children should be irrelevant
		}
		argsCanonSwapped = swap;
	}

	protected int getDataIndex(int index) {
		return argsCanonSwapped ? parent.children.size() - 1 - index : index;
	}

	@Override
	public String type() {
		return "highlight";
	}

	// If we get bad parameters, we want to log the trace of how it happened, but we don't
	// want to throw the exception until this hint is read, so as not to interrupt the
	// rest of the hint generation
	protected void storeException(String message) {
		try {
			throw new RuntimeException(message);
		} catch (RuntimeException e) {
			this.e = e;
		}
	}

	@Override
	public final JSONObject data() {
		return data(false);
	}

	public JSONObject data(boolean refNodeIDs) {
		if (e != null) throw e;
		JSONObject data = new JSONObject();
		putNodeReference(data, "parent", parent, refNodeIDs);
		data.put("action", action());
		LinkedList<String> items = getParentChildren();
		data.put("from", toJSONArray(items, argsCanonSwapped));
		editChildren(items);
		data.put("to", toJSONArray(items, argsCanonSwapped));
		return data;
	}

	protected void putNodeReference(JSONObject data, String key, Node node, boolean refNodeIDs) {
		if (node == null) {
			data.put(key, (String) null);
			return;
		}
		if (refNodeIDs) {
			data.put(key, node.id);
		} else {
			data.put(key, Node.getNodeReference(node));
		}
	}

	private JSONArray toJSONArray(LinkedList<String> items, boolean swapArgs) {
		JSONArray array = new JSONArray();
		for (int i = 0; i < items.size(); i++) {
			array.put(items.get(swapArgs ? items.size() - 1 - i : i));
		}
		return array;
	}

	protected LinkedList<String> getParentChildren() {
		if (parent == null) return new LinkedList<>();
		return new LinkedList<>(Arrays.asList(
				useValues ? parent.getChildTypeValueArray() : parent.getChildArray()
		));
	}

	@Override
	public String from() {
		if (e != null) throw e;
		LinkedList<String> items = getParentChildren();
		if (argsCanonSwapped) Collections.reverse(items);
		return action() + ": " + rootString(parent) + ": " + items;
	}

	@Override
	public String to() {
		if (e != null) throw e;
		LinkedList<String> items = getParentChildren();
		editChildren(items);
		if (argsCanonSwapped) Collections.reverse(items);
		return action() + ": " + rootString(parent) + ": " + items;
	}

	protected String getID(Node node) {
		return "";
	}

	protected String rootString(Node node) {
		return node.rootPathString();
	}

	@Override
	public String toString() {
		LinkedList<String> items = getParentChildren();
		String from = items.toString();
		editChildren(items);
		String to = items.toString();
		String diff = Diff.inlineDiff(from, to, "[\\[|\\]|,|\\s]");
		return action().substring(0, 1) + ": " + rootString(parent) + ": " + diff;
	}

	@Override
	public final int compareTo(EditHint o) {
		return Double.compare(o.priority(), priority());
	}

	public static void applyEdits(Node root, List<EditHint> hints) {
		List<Application> applications = new ArrayList<>();
		for (EditHint hint : hints) {
			Node editParent = Node.findMatchingNodeInCopy(hint.parent, root);
			hint.addApplications(root, editParent, applications);
		}

		// Start with hints in reverse order, since multiple inserts at the same index should
		// be applied in reverse to create the correct final order
		Collections.sort(applications, new Comparator<Application>() {
			@Override
			public int compare(Application o1, Application o2) {
				// Apply edits to longer root paths first
				int rpc = -Integer.compare(
						o1.parent.rootPathLength(), o2.parent.rootPathLength());
				if (rpc != 0) return rpc;

				int ic = -Integer.compare(o1.index, o2.index);
				if (ic != 0) return ic;

				// In the case of insertions at the same index, we compare the pair index
				// to insert the in the same order they were the pair code
				return -Integer.compare(o1.pairIndex, o2.pairIndex);
			}
		});
		for (Application application : applications) application.action.apply();
	}


	protected static class Application {
		final Node parent;
		final int index;
		final int pairIndex;
		final EditAction action;

		public Application(Node parent, int index, EditAction action) {
			// Non-insertions go first
			this(parent, index, Integer.MAX_VALUE, action);
		}

		public Application(Node parent, int index, int pairIndex, EditAction action) {
			this.parent = parent;
			this.index = index;
			this.pairIndex = pairIndex;
			this.action = action;
		}
	}

	protected static boolean nodesIDEqual(Node a, Node b) {
		return a == b || (a != null && b != null && StringUtils.equals(a.id, b.id));
	}

	protected static int nodeIDHashCode(Node node) {
		if (node.id != null) return node.id.hashCode();
		return node.hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		if (obj == null) return false;
		if (obj == this) return true;
		if (obj.getClass() != getClass()) return false;
		EditHint rhs = (EditHint) obj;
		EqualsBuilder builder = new EqualsBuilder() {
			@Override
			public EqualsBuilder append(Object lhs, Object rhs) {
				if (lhs instanceof Node && rhs instanceof Node) {
					return appendSuper(nodesIDEqual((Node) lhs, (Node) rhs));
				}
				return super.append(lhs, rhs);
			}
		};
		builder.append(parent, rhs.parent);
		builder.append(argsCanonSwapped, rhs.argsCanonSwapped);
		appendEqualsFieds(builder, rhs);
		return builder.isEquals();
	}

	@Override
	public final int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder(5, 3) {
			@Override
			public HashCodeBuilder append(Object object) {
				if (object instanceof Node) {
					return super.append(nodeIDHashCode((Node) object));
				}
				return super.append(object);
			}
		};
		builder.append(getClass());
		builder.append(parent);
		builder.append(argsCanonSwapped);
		appendHashCodeFieds(builder);
		return builder.toHashCode();
	}

	interface EditAction {
		void apply();
	}
}