package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class DefBlockNode extends DefaultNode {

	protected List<BNode> variants;

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	protected List<BNode> getVariants(Context context) {
		variants = super.getVariants(context);
		context.blocksDefs.put(name, this);
		return new LinkedList<>();
	}

}
