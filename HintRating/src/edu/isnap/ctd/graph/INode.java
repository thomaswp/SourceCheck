package edu.isnap.ctd.graph;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface INode {
	public String type();
	public String value();
	public String id();
	public INode parent();
	public List<? extends INode> children();

	public default int index() {
		if (parent() == null) return -1;
		List<? extends INode> siblings = parent().children();
		for (int i = 0; i < siblings.size(); i++) {
			if (siblings.get(i) == this) {
				return i;
			}
		}
		return -1;
	}

	public default INode search(Predicate<INode> predicate) {
		if (predicate.test(this)) return this;
		for (INode node : children()) {
			INode found = node.search(predicate);
			if (found != null) return found;
		}
		return null;
	}

	public default boolean hasType(String... types) {
		for (String type : types) {
			if (type.equals(this.type())) return true;
		}
		return false;
	}

	public static void recurse(INode node, Consumer<INode> action) {
		action.accept(node);
		for (INode child : node.children()) {
			if (child != null) recurse(child, action);
		}
	}
}
