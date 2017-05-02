package edu.isnap.template.data;

import java.util.List;

public class BlockNode extends DefaultNode {

	@Override
	protected List<BNode> getVariants(Context context) {
		if (!context.blocksDefs.containsKey(name)) {
			throw new RuntimeException("No block def: " + name);
		}

		DefBlockNode blockDef = context.blocksDefs.get(name);
		return blockDef.variants;
	}

}
