package edu.isnap.ctd.graph;

import java.util.List;
import java.util.function.Consumer;

public interface INode {
	public String type();
	public String value();
	public String id();
	public INode parent();
	public List<? extends INode> children();

	public static void recurse(INode node, Consumer<INode> action) {
		action.accept(node);
		for (INode child : node.children()) {
			if (child != null) recurse(child, action);
		}
	}
}
