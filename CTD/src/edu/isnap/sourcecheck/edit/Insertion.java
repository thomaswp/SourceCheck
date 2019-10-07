package edu.isnap.sourcecheck.edit;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.hint.TextHint;
import edu.isnap.node.ASTNode;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Predicate;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.util.map.BiMap;

public class Insertion extends EditHint {
	public final String type;
	public final String value;
	public final int index;
	public final boolean missingParent;
	// Used when applying insertions with replacements to determine whether the original node's
	// children should be copied to the replacement
	public boolean keepChildrenInReplacement;

	// Used to mark the pair node in the solution this insertion represents
	public final transient Node pair;

	// For nodes with missing parents, we expect to be given a parent from the pair. We copy that
	// node to create the parent used, but we store the original pair here.
	public final transient Node parentPair;

	/** The node the inserted node should replace (in the same location) */
	public Node replaced;
	/** A candidate node elsewhere that could be used to do the replacement */
	public Node candidate;

	public final List<TextHint> textHints = new ArrayList<>();

	@Override
	public String action() {
		return "insert";
	}

	public Insertion(Node parent, Node pair, int index, String value) {
		this(parent, pair, index, value, false);
	}

	private static Node cloneParentIfMissing(Node parent, Node pair, boolean missingParent) {
		if (!missingParent) return parent;
		// If the parent is missing, we clone the pair-parent and remove the pair, so the insert
		// text is descriptive
		Node parentClone = pair.parent.copy();
		parentClone.children.remove(pair.index());
		return parentClone;
	}

	public Insertion(Node parent, Node pair, int index, String value, boolean missingParent) {
		super(cloneParentIfMissing(parent, pair, missingParent));
		this.type = pair.type();
		this.value = value;
		this.index = index;
		this.missingParent = missingParent;
		this.parentPair = missingParent ? parent : null;
		this.pair = pair;
		if (pair != null) {
			textHints.addAll(pair.readOnlyAnnotations().getHints());
		}
		if (index > parent.children.size()) {
			storeException("Insert index out of range");
		}
	}

	@Override
	public JSONObject data(boolean refNodeIDs) {
		JSONObject data = super.data(refNodeIDs);
		data.put("missingParent", missingParent);
		data.put("index", getDataIndex(index));
		data.put("type", type);
		putNodeReference(data, "replacement", replaced, refNodeIDs);
		putNodeReference(data, "candidate", candidate, refNodeIDs);
		JSONArray hints = new JSONArray();
		for (TextHint hint : textHints) hints.put(hint.toJSON());
		data.put("textHints", hints);
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

	@Override
	protected void editChildren(List<String> children) {
		if (replaced != null) {
			children.remove(index);
		}
		children.add(index, type + ((!useValues || value == null) ? "" : (":" + value)));
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
	protected boolean shouldHaveParent() {
		return !missingParent;
	}

	@Override
	protected void addApplications(Node root, Node editParent, List<Application> applications) {
		Node candidate = Node.findMatchingNodeInCopy(this.candidate, root);
		Node replaced = Node.findMatchingNodeInCopy(this.replaced, root);
		if (candidate != null) {
			applications.add(new Application(candidate.parent, candidate.index(),
					new EditAction() {
				@Override
				public void apply(BiMap<Node, Node> createdNodeMap) {
					// It's possible this has already been removed (e.g. as a replacement for
					// another insert), so we only remove it if it's still a child of its parent
					int index = candidate.index();
					if (index >= 0) candidate.parent.children.remove(index);
				}
			}));
		}

		applications.add(new Application(editParent, index, pair.index(), new EditAction() {
			@Override
			public void apply(BiMap<Node, Node> createdNodeMap) {
				int index = Insertion.this.index;
				Node parent = editParent;

				// If this node had a missing parent, but it has been added by an earlier
				// insertion, find the inserted node in the createdNodeMap and use that.
				// Otherwise, we cannot add this node, so return.
				if (missingParent) {
					parent = createdNodeMap.getTo(parentPair);
					if (parent == null) return;
				}

				Node toInsert;
				if (candidate != null) {
					// Need to use the actual candidate in case other applications edit its children
					// but this will cause its parent to be incorrect. This is ok, since we actually
					// need its original parent to be used in the removal below, but still seems
					// like a bad idea, so in short:
					// TODO: fix this
					toInsert = candidate; //.copyWithNewParent(parent);
				} else {
					toInsert = root.constructNode(editParent, type, value, null);
					createdNodeMap.put(toInsert, pair);
				}

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

				// In case this is a newly inserted parent, we pad with placeholder nodes
				while (parent.children.size() < index) {
					parent.children.add(parent.constructNode(parent, ASTNode.EMPTY_TYPE));
				}
				// and then remove them as children are inserted
				if (replaced == null && index < parent.children.size() &&
						parent.children.get(index).hasType(ASTNode.EMPTY_TYPE)) {
					parent.children.remove(index);
				}
				parent.children.add(index, toInsert);
			}
		}));
	}

	@Override
	protected void appendEqualsFieds(EqualsBuilder builder, EditHint rhs) {
		Insertion ins = (Insertion) rhs;
		builder.append(type, ins.type);
		builder.append(value, ins.value);
		builder.append(index, ins.index);
		builder.append(missingParent, ins.missingParent);
		builder.append(keepChildrenInReplacement, ins.keepChildrenInReplacement);
		builder.append(replaced, ins.replaced);
		builder.append(candidate, ins.candidate);
	}

	@Override
	protected void appendHashCodeFieds(HashCodeBuilder builder) {
		builder.append(type);
		builder.append(value);
		builder.append(index);
		builder.append(missingParent);
		builder.append(keepChildrenInReplacement);
		builder.append(replaced);
		builder.append(candidate);
	}

	@Override
	protected Object getParentForComparison() {
		if (!missingParent) return super.getParentForComparison();
		// We consider two moves (to a not-yet-existant parent) to be equivalent if the new parents
		// have the same type, even if they're in different places. In iSnap these are simply
		// displayed as highlights without indicating the destination. This might need to change
		// later if more details from these hints are given.
		return parent.type();
	}

	@Override
	public Node getPriorityToNode(Mapping mapping) {
		return pair;
	}

	@Override
	public SourceLocation getCorrectedEditStart() {
		ASTNode node = null;

		if (this.replaced != null) {//if there's a replaced, the new code should go right after the replaced location. Cross out the replace, add the contents of the pair
			node = (ASTNode) this.replaced.tag;
		} else {//else, take the parent, which may or may not have children. The Insertion's index property is the index at which we want to insert in the parent

		}
//		don't do this, candidate is where it used to be, not where it should go
//		if (this.candidate != null /*&& !this.missingParent*/) { node = (ASTNode) this.candidate.tag; } //TODO: investigate this

		if (node != null) {
			// TODO: There are some times when the replaced will have no start source (e.g. null)
			return node.startSourceLocation;
		}
		return null;
	}

	@Override
	public SourceLocation getCorrectedEditEnd() {
		ASTNode node = null;

		if (this.replaced != null) {//if there's a replaced, the new code should go right after the replaced location. Cross out the replace, add the contents of the pair
			node = (ASTNode) this.replaced.tag;
		} else {//else, take the parent, which may or may not have children. The Insertion's index property is the index at which we want to insert in the parent

		}
//		don't do this, candidate is where it used to be, not where it should go
//		if (this.candidate != null /*&& !this.missingParent*/) { node = (ASTNode) this.candidate.tag; } //TODO: investigate this

		if (node != null) {
			return node.endSourceLocation;
		}
		return null;
	}

	@Override
	public EditType getEditType() {
		if (this.replaced != null) { return EditType.REPLACEMENT; }
		if (this.candidate != null && !this.missingParent) {return EditType.CANDIDATE;}
		return EditType.INSERTION;
	}
}