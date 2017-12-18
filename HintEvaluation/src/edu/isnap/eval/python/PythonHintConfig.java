package edu.isnap.eval.python;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;

public class PythonHintConfig extends HintConfig {
	private static final long serialVersionUID = 1L;

	public PythonHintConfig() {
		preprocessSolutions = false;
	}

	@Override
	public boolean isOrderInvariant(String type) {
		return false;
	}

	@Override
	public boolean hasFixedChildren(Node node) {
		return node != null && !"list".equals(node.type());
	}

	@Override
	public boolean canMove(Node node) {
		return node != null && !"list".equals(node.type());
	}

	@Override
	public boolean isContainer(String type) {
		return false;
	}

	private final static String[][] valueMappedTypes = new String[][] {
			new String[] { "Name", "arg", "alias" },
			new String[] { "FunctionDef" },
	};

	@Override
	public String[][] getValueMappedTypes() {
		return valueMappedTypes;
	}

	@Override
	public boolean shouldAutoAdd(Node node) {
		if (node == null || node.parent == null) return false;
		Node parent = node.parent;
		int index = node.index();
		return (parent.hasType("BinOp") && index == 1) ||
				(parent.hasType("UnaryOp") && index == 0) ||
				(parent.hasType("BoolOp") && index == 0) ||
				(parent.hasType("Compare") && index == 1) ||
				// Children of auto-added lists (e.g. in compare) should also be auto-added
				(parent.hasType("list") && shouldAutoAdd(parent)) ||
				node.hasType("Load", "Store", "Del");
	}

//	@Override
//	public String getValueMappingClass(Node node) {
//		if (node == null) return null;
//		if (node.hasType("FunctionDef")) return node.type();
//		if (node.hasType("Name", "arg")) {
//			// Names are not interchangeable if they're values (e.g. foo in foo.bar)
//			if (node.parentHasType("Attribute")) return null;
//		}
//		return null;
//	}
}
