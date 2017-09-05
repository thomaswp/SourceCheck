package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class DefVarNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		String name = name();
		String oldValue = context.varDefs.get(name);

		List<String> values = new LinkedList<>(args);
		values.remove(0);
		if (values.size() == 0) {
			values.add("false");
			values.add("true");
		}

		List<BNode> variants = new LinkedList<>();
		for (String value : values) {
			context.varDefs.put(name, value);
			variants.addAll(super.getVariants(context));
		}

		context.varDefs.put(name, oldValue);
		return variants;
	}

}
