package astrecognition.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tree extends Graph {
	protected Tree parent;
	protected List<Tree> children;
	private String label;
	private int id;
	private int lineNumber;
	private int startPosition;
	private int endPosition;

	public Tree(Tree parent, String label) {
		this.label = label;
		this.parent = parent;
		this.children = new ArrayList<Tree>();
		this.lineNumber = 0;
		this.id = 0;
		this.startPosition = 0;
		this.endPosition = 0;
	}

	public Tree(String label) {
		this(null, label);
	}

	public String getOriginalLabel() {
		return this.label;
	}

	public void setOriginalLabel(String originalLabel) {
		this.label = originalLabel;
	}

	public String getUniqueLabel() {
		String uniqueLabel = this.label;
		if (this.id > 0) {
			uniqueLabel += ":" + this.id;
		}
		return uniqueLabel;
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getStartPosition() {
		return this.startPosition;
	}

	public void setStartPosition(int startPosition) {
		this.startPosition = startPosition;
	}

	public int getEndPosition() {
		return this.endPosition;
	}

	public void setEndPosition(int endPosition) {
		this.endPosition = endPosition;
	}

	public Tree getParent() {
		return this.parent;
	}

	public void setParent(Tree parent) {
		this.parent = parent;
	}

	public int addChild(Tree tree) {
		this.children.add(tree);
		return this.children.size() - 1;
	}

	public void deleteChild(int position) {
		this.children.remove(position);
	}

	public List<Tree> getChildren() {
		return this.children;
	}

	public int getPositionBetween(Graph left, Graph right) {
		if (left == null) {
			return 0;
		} else if (right == null) {
			return this.children.size();
		}
		return this.children.indexOf(left) + 1;
	}

	public boolean isDescendant(Tree tree) {
		if (children.contains(tree)) {
			return true;
		}

		for (Graph t : children) {
			if (((Tree) t).isDescendant(tree)) {
				return true;
			}
		}

		return false;
	}

	public Tree find(String label) {
		if (this.getUniqueLabel().equals(label)) {
			return this;
		} else {
			for (Graph t : this.children) {
				Tree goal = ((Tree) t).find(label);
				if (null != goal) {
					return goal;
				}
			}
		}
		return null;
	}

	private boolean isTopLevelElement() {
		// Need to restart the labeling at top level elements, so this is our
		// place to indicate what is top-level right now
		return this.getOriginalLabel().equals("MethodDeclaration");
	}

	public Tree makeLabelsUnique(Map<String, Integer> labelIds) {
		if (this.id == 0) {
			if (this.isTopLevelElement()) {
				labelIds = new HashMap<String, Integer>();
			}
			if (labelIds.containsKey(this.getOriginalLabel())) {
				int currentValue = labelIds.get(this.getOriginalLabel());
				labelIds.put(this.getOriginalLabel(), currentValue + 1);
				this.setId(currentValue + 1);
			} else {
				labelIds.put(this.getOriginalLabel(), 0);
			}
			for (Graph child : this.children) {
				((Tree) child).makeLabelsUnique(labelIds);
			}
		}

		return this;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	public int getLineNumber() {
		return this.lineNumber;
	}

	public String toString() {
		return this.getUniqueLabel();
	}

	@Override
	public int compareTo(Graph graph) {
		if (graph instanceof Tree) {
			return this.getUniqueLabel().compareTo(
					((Tree) graph).getUniqueLabel());
		}
		return -1;
	}

	public boolean isLeaf() {
		return this.children.size() == 0;
	}

	@Override
	public boolean isSink() {
		return this.isLeaf();
	}

	@Override
	public Collection<Graph> getConnections() {
		Collection<Graph> connections = new ArrayList<Graph>();
		for (Tree tree : this.getChildren()) {
			connections.add((Graph) tree);
		}
		return connections;
	}

	@Override
	public String getLabel() {
		return this.label;
	}

	@Override
	public int getTotalNodeCount() {
		int totalNodes = 1; // current node

		for (Tree t : this.children) {
			totalNodes += t.getTotalNodeCount();
		}
		
		return totalNodes;
	}
}
