package edu.isnap.template.data;

import java.util.List;

public class AnyOrderNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		List<BNode> variants = super.getVariants(context);
		int group = context.nextOrderGroup();
		for (BNode variant : variants) {
			for (BNode child : variant.children) {
				child.orderGroup = group;
			}
		}
		return variants;
	}

}
