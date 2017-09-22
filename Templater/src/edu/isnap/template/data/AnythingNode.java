package edu.isnap.template.data;

import java.util.Arrays;
import java.util.List;

public class AnythingNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		if (children.size() > 0) {
			throw new RuntimeException("AnythingNode cannot have children");
		}

		BNode node = new BNode("@anything", inline(), context);
		node.anything = true;
		return Arrays.asList(node);
	}
}
