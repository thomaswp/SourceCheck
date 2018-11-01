package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

import edu.isnap.hint.TextHint;

public class DefHintNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		BNode children = super.getVariants(context).get(0);
		if (children.children.isEmpty()) {
			throw new RuntimeException("Hint defined with no children: " + name());
		}
		int priority = 0;
		String text = children.children.get(0).type;
		for (BNode child : children.children) {
			if ("priority".equals(child.type)) {
				priority = Integer.parseInt(child.children.get(0).type);
			}
		}
		context.hints.put(name(), new TextHint(text, priority));
		return new LinkedList<>();
	}

}
