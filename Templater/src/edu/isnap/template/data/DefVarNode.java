package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class DefVarNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	protected List<BNode> getVariants(Context context) {
		Boolean oldValue = context.varDefs.get(name);
		List<BNode> variants = new LinkedList<>();
		context.varDefs.put(name, true);
		variants.addAll(super.getVariants(context));
		context.varDefs.put(name, false);
		variants.addAll(super.getVariants(context));
		context.varDefs.put(name, oldValue);
		return variants;
	}

}
