package edu.isnap.template.data;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DefaultNode {
	public String type;
	public String name;
	public List<DefaultNode> children = new LinkedList<>();

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

	protected List<BNode> getVariants(Context context) {
		List<BNode> variants = new LinkedList<>();

		if (children.size() == 0) {
			variants.add(new BNode(type));
			return variants;
		}

		List<List<BNode>> childVariants = new LinkedList<>();

		for (DefaultNode child : children) {
			childVariants.add(child.getVariants(context));
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
			BNode root = new BNode(type);
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
			default: node = new DefaultNode();
		}
		node.type = type;
		return node;

	}
}
