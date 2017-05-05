package edu.isnap.template.data;

import java.util.ArrayList;
import java.util.List;

public class RepeatNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		int min = Integer.parseInt(args.get(0)), max = min;
		if (args.size() > 1) max = Integer.parseInt(args.get(1));

		List<BNode> variants = new ArrayList<>();
		for (int i = min; i <= max; i++) {
			List<BNode> set = super.getVariants(context);
			for (BNode var : set) {
				List<BNode> children = new ArrayList<>(var.children);
				var.children.clear();
				for (int j = 0; j < i; j++) {
					var.children.addAll(children);
				}
			}
			variants.addAll(set);
		}
		return variants;
	}

}
