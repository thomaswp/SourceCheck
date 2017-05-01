package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class OrNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	protected List<BNode> getVariants(Context context) {
		List<BNode> variants = new LinkedList<>();
		for (DefaultNode child : children) {
			variants.addAll(child.getVariants(context));
		}
		return variants;
	}
}
