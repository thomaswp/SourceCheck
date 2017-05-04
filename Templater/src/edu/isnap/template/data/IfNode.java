package edu.isnap.template.data;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
	public List<BNode> getVariants(Context context) {
		Set<String> values = new HashSet<>();
		if (args.size() == 1) {
			values.add("true");
		} else {
			for (int i = 1; i < args.size(); i++) {
				values.add(args.get(i));
			}
		}

		String name = name();
		if (!context.varDefs.containsKey(name)) throw new RuntimeException("No var def: " + name);
		if (values.contains(context.varDefs.get(name)) == ifTrue) {
			return super.getVariants(context);
		} else {
			return new LinkedList<>();
		}
	}

}
