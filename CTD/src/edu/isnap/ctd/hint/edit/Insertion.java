package edu.isnap.ctd.hint.edit;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Predicate;

public class Insertion extends EditHint {
	public final String type;
	public final int index;
	public final boolean missingParent;
	// Used when applying insertions with replacements to determine whether the original node's
	// children should be copied to the replacement
	public boolean keepChildrenInReplacement;

	// Used to mark the pair node in the solution this insertion represents
	public final transient Node pair;

	/** The node the inserted node should replace (in the same location) */
	public Node replaced;
	/** A candidate node elsewhere that could be used to do the replacement */
	public Node candidate;

	@Override
	public String action() {
		return "insert";
	}

	public Insertion(Node parent, Node pair, int index) {
		this(parent, pair, index, false);
	}

	public Insertion(Node parent, Node pair, int index, boolean missingParent) {
		super(parent);
		this.type = pair.type();
		this.index = index;
		this.missingParent = missingParent;
		this.pair = pair;
		if (index > parent.children.size()) {
			storeException("Insert index out of range");
		}
	}

	@Override
	public JSONObject data() {
		JSONObject data = super.data();
		data.put("missingParent", missingParent);
		data.put("index", getDataIndex(index));
		data.put("type", type);
		data.put("replacement", Node.getNodeReference(replaced));
		data.put("candidate", Node.getNodeReference(candidate));
		LinkedList<String> items = new LinkedList<>(Arrays.asList(parent.getChildArray()));
		data.put("from", toJSONArray(items, argsCanonSwapped));
		editChildren(items);
		data.put("to", toJSONArray(items, argsCanonSwapped));
		return data;
	}

	@Override
	protected String rootString(Node node) {
		// Mark missing parents in the preview
		String rootString = super.rootString(node);
		if (missingParent) {
			rootString = "{" + rootString + "}";
		}
		return rootString;
	}

	private JSONArray toJSONArray(LinkedList<String> items, boolean swapArgs) {
		JSONArray array = new JSONArray();
		for (int i = 0; i < items.size(); i++) {
			array.put(items.get(swapArgs ? items.size() - 1 - i : i));
		}
		return array;
	}

	@Override
	protected void editChildren(List<String> children) {
		if (replaced != null) {
			children.remove(index);
		}
		children.add(index, type);
	}

	@Override
	public String from() {
		String text = super.from();
		if (candidate != null) {
			text += " using " + super.rootString(candidate);
		}
		return text;
	}

	@Override
	public String to() {
		String text = super.to();
		if (candidate != null) {
			text += " using " + super.rootString(candidate);
		}
		return text;
	}

	@Override
	protected double priority() {
		return 3 + (candidate == null ? 0  : 0.1) + (replaced == null ? 0 : 0.1);
	}

	@Override
	public String toString() {
		String base = super.toString();
		if (candidate == null) return base;
		base += " w/ " + rootString(candidate);
		return base;
	}

	@Override
	public void apply(List<Application> applications) {
		final Node toInsert;
		if (candidate != null) {
			// Need to use the actual candidate in case other applications edit its children
			// but this will cause its parent to be incorrect. This is ok, since we actually
			// need its original parent to be used in the removal below, but still seems
			// like a bad idea, so in short:
			// TODO: fix this
			toInsert = candidate; //.copyWithNewParent(parent);

			applications.add(new Application(candidate.parent, candidate.index(),
					new EditAction() {
				@Override
				public void apply() {
					// It's possible this has already been removed (e.g. as a replacement for
					// another insert), so we only remove it if it's still a child of its parent
					int index = candidate.index();
					if (index >= 0) candidate.parent.children.remove(index);
				}
			}));
		} else {
			toInsert = new Node(parent, type);
		}

		// If the parent is missing, we stop after removing the candidate
		if (missingParent) return;

		applications.add(new Application(parent, index, pair.index(), new EditAction() {
			@Override
			public void apply() {
				int index = Insertion.this.index;
				Node parent = Insertion.this.parent;
				if (replaced != null) {
					int rIndex = replaced.index();
					if (rIndex >= 0) {
						index = rIndex;
						replaced.parent.children.remove(rIndex);
					} else if (keepChildrenInReplacement) {
						// Special case for renamed nodes
						// TODO: as most special cases, this is a bit of a hack
						// If the replacement has been moved, we want to relabel the replacement
						// in its new locations, so we find its new parent, remove it and
						// set parent and index appropriately
						Node newParent = parent.root().search(new Predicate() {
							@Override
							public boolean eval(Node node) {
								for (Node child : node.children) {
									if (child == replaced) return true;
								}
								return false;
							}
						});
						if (newParent != null) {
							parent = newParent;
							for (int i = 0; i < parent.children.size(); i++) {
								if (parent.children.get(i) == replaced) {
									index = i;
									parent.children.remove(i);
									break;
								}
							}
						}
					}
					if (keepChildrenInReplacement) {
						for (Node child : replaced.children) {
							toInsert.children.add(child.copyWithNewParent(toInsert));
						}
					}
				}
				parent.children.add(index, toInsert);
			}
		}));
	}
}