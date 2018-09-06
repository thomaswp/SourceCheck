package edu.isnap.template.data;

import java.util.List;

import edu.isnap.ctd.hint.TextHint;

public class HintNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		List<BNode> variants = super.getVariants(context);
		TextHint hint = context.hints.get(name());
		for (BNode variant : variants) {
			variant.hints.add(hint);
		}
		return variants;
	}

}
