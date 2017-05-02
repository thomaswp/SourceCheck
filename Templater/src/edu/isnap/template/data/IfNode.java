package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class IfNode extends DefaultNode {

	public final boolean ifTrue;

	public IfNode(boolean ifTrue) {
		this.ifTrue = ifTrue;
	}

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	protected List<BNode> getVariants(Context context) {
		if (!context.varDefs.containsKey(name)) throw new RuntimeException("No var def: " + name);
		if (context.varDefs.get(name) == ifTrue) {
			return super.getVariants(context);
		} else {
			return new LinkedList<>();
		}
	}

}
