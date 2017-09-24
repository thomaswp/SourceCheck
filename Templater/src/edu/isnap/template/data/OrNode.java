package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class OrNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		List<BNode> variants = new LinkedList<>();
		for (DefaultNode child : children) {
			variants.addAll(child.getVariants(context));
			// If optional, only add the first possibility
			if (context.stopOptional(optional)) break;
		}
		return variants;
	}
}
