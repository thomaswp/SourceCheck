package edu.isnap.ctd.hint.edit;

import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.map.BiMap;

public class Reorder extends EditHint {
	public final Node node;
	public final int index;
	// If a reorder is in-place, it means it is for a code-element, and the children do not
	// "shift" when a node is removed.
	public final boolean inPlace;

	@Override
	protected String action() {
		return "reorder";
	}

	public Reorder(Node node, int index, boolean inPlace) {
		super(node.parent);
		this.node = node;
		this.index = index;
		this.inPlace = inPlace;
		if (index < 0 || index > node.parent.children.size()) {
			storeException("Reorder index out of bounds: " + index + " for size " +
					node.parent.children.size());
		}
		// A reorder is empty if it keeps its index _or_ it it is set to insert "after itself"
		// in a script, which happens if inPlace is false and the index is the node's plus 1
		if (index == node.index() || (!inPlace && index == node.index() + 1)) {
			storeException("Empty reorder");
		}
	}

	public boolean shouldSuppress(BiMap<Node, Node> mapping) {
		// If these have the same orderGroup, there's no need to reorder. Ideally this
		// would be all handled by NodeAlignment, but it's possible for it to mismatch
		// some nodes of the same type

		int rIndex = node.index();
		int aIndex = index;
		// Adjust the add index if the remove index is less than it
		if (rIndex < aIndex && !inPlace) aIndex--;
		if (aIndex >= 0 && aIndex < parent.children.size()) {
			Node displacedNode = node.parent.children.get(aIndex);
			// We need to get the node pairs from the mapping because only to-nodes have
			// order groups
			Node nodePair = mapping.getFrom(node);
			Node displacedNodePair = mapping.getFrom(displacedNode);
			if (nodePair == null || displacedNodePair == null) return false;
			int groupA = nodePair.readOnlyAnnotations().orderGroup;
			int groupB = displacedNodePair.readOnlyAnnotations().orderGroup;
			if (groupA == groupB && groupA != 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	public JSONObject data() {
		JSONObject data = super.data();
		data.put("node", Node.getNodeReference(node));
		data.put("index", getDataIndex(index));
		return data;
	}

	@Override
	protected void editChildren(List<String> children) {
		int rIndex = node.index();
		int aIndex = index;
		if (rIndex < aIndex && !inPlace) aIndex--;
		children.add(aIndex, children.remove(rIndex));
	}

	@Override
	protected double priority() {
		return 5;
	}

	@Override
	public void apply(List<Application> applications) {
		final int rIndex = node.index();
		applications.add(new Application(parent, rIndex, new EditAction() {
			@Override
			public void apply() {
				node.parent.children.remove(rIndex);
			}
		}));
		final int aIndex = index;
		applications.add(new Application(parent, aIndex, new EditAction() {
			@Override
			public void apply() {
				node.parent.children.add(aIndex, node);
			}
		}));
	}
}