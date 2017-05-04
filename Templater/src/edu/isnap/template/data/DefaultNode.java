package edu.isnap.template.data;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DefaultNode {
	public String type;
	public List<String> args = new LinkedList<>();
	public List<DefaultNode> children = new LinkedList<>();

	public String name() {
		return args.size() == 0 ? null : args.get(0);
	}

	@Override
	public String toString() {
		return type + (children.size() == 0 ? "" : (" " + children));
	}

	public boolean inline() {
		return false;
	}

	final public List<BNode> getVariants() {
		return getVariants(new Context());
	}

	public List<BNode> getVariants(Context context) {

		if (children.size() == 0) {
			BNode node = new BNode(type, inline());
			Integer litArgs = context.defaultAgs.get(type);
			if (litArgs != null) {
				for (int i = 0; i < litArgs; i++) {
					node.children.add(new BNode("literal", false));
				}
			}

			List<BNode> variants = new LinkedList<>();
			variants.add(node);
			return variants;
		}

		List<List<BNode>> childVariants = new LinkedList<>();

		for (DefaultNode child : children) {
			List<BNode> vars = child.getVariants(context);
			if (vars.size() > 0) {
				childVariants.add(vars);
			}
		}

		return getVariants(childVariants);
	}

	protected List<BNode> getVariants(List<List<BNode>> childVariants) {
		int size = 1;
		for (List<BNode> list : childVariants) {
			size *= list.size();
		}

		List<BNode> list = new ArrayList<>(size);

		for (int i = 0; i < size; i++) {
			BNode root = new BNode(type, inline());
			int offset = 1;
			for (int j = 0; j < childVariants.size(); j++) {
				List<BNode> variants = childVariants.get(j);
				int index = (i / offset) % variants.size();
				root.children.add(variants.get(index));
				offset *= variants.size();
			}
			list.add(root);
		}

		return list;
	}

	public static DefaultNode create(String type) {
		DefaultNode node;
		switch (type) {
			case "@or": node = new OrNode(); break;
			case "@defBlock": node = new DefBlockNode(); break;
			case "@block": node = new BlockNode(); break;
			case "@defVar": node = new DefVarNode(); break;
			case "@if": node = new IfNode(true); break;
			case "@unless": node = new IfNode(false); break;
			case "@anyOrder": node = new AnyOrderNode(); break;
			default:
				if (type.startsWith("@")) {
					throw new RuntimeException("Unnown annotation: " + type);
				}
				node = new DefaultNode();
		}
		node.type = type;
		return node;

	}
}
