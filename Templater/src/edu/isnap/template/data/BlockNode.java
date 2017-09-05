package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

public class BlockNode extends DefaultNode {

	@Override
	public List<BNode> getVariants(Context context) {
		if (!context.blocksDefs.containsKey(name())) {
			throw new RuntimeException("No block def: " + name());
		}

		DefBlockNode blockDef = context.blocksDefs.get(name());
		List<BNode> variants = new LinkedList<>();
		for (BNode node : blockDef.variants) {
			variants.add(node.copy());
		}
		return variants;
	}

}
