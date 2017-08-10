package edu.isnap.ctd.hint.edit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.Canonicalization;
import edu.isnap.ctd.hint.Hint;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.Canonicalization.InvertOp;
import edu.isnap.ctd.hint.Canonicalization.SwapSymmetricArgs;

public abstract class EditHint implements Hint, Comparable<EditHint> {
	protected abstract void editChildren(List<String> children);
	protected abstract String action();
	protected abstract double priority();
	public abstract void apply(List<Application> applications);

	public final Node parent;

	protected final boolean argsCanonSwapped;
	protected transient RuntimeException e;

	public EditHint(Node parent) {
		this.parent = parent;

		boolean swap = false;
		for (Canonicalization c : parent.canonicalizations) {
			if (c instanceof InvertOp || c instanceof SwapSymmetricArgs) {
				swap = true;
				break;
			}
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
	public JSONObject data() {
		if (e != null) throw e;
		JSONObject data = new JSONObject();
		data.put("parent", Node.getNodeReference(parent));
		data.put("action", action());
		return data;
	}

	private LinkedList<String> getParentChildren() {
		if (parent == null) return new LinkedList<>();
		return new LinkedList<>(Arrays.asList(parent.getChildArray()));
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
		return HintMap.toRootPath(node).root().toString();
	}

	@Override
	public String toString() {
		LinkedList<String> items = new LinkedList<>(Arrays.asList(parent.getChildArray()));
		String from = items.toString();
		editChildren(items);
		String to = items.toString();
		return action().substring(0, 1) + ": " + rootString(parent) + ": " + from + " -> " + to;
	}

	@Override
	public int compareTo(EditHint o) {
		return Double.compare(o.priority(), priority());
	}

	public static void applyEdits(Node node, List<EditHint> hints) {
		List<Application> applications = new ArrayList<>();
		for (EditHint hint : hints) hint.apply(applications);

		// Start with hints in reverse order, since multiple inserts at the same index should
		// be applies in reverse to create the correct final order
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

	interface EditAction {
		void apply();
	}
}