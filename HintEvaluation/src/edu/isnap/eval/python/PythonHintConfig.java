package edu.isnap.eval.python;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.NodeConstructor;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.eval.python.PythonImport.PythonNode;

public class PythonHintConfig extends HintConfig {
	private static final long serialVersionUID = 1L;

	public PythonHintConfig() {
		preprocessSolutions = false;
	}

	@Override
	public boolean isOrderInvariant(String type) {
		return false;
	}

	// Some nodes in python have list children that almost always have a single child, such as
	// comparison and assignment operators. This allows the children of these to be replaced, as
	// fixed arguments, rather than added and removed separately as statements.
	private final static String[] usuallySingleListParents = new String[] {
			"Compare",
			"Assign",
	};

	@Override
	public boolean hasFixedChildren(Node node) {
		return node != null &&
				(!"list".equals(node.type()) || node.parentHasType(usuallySingleListParents));
	}

	// No hint should suggest moving lists or most literal types
	private final Set<String> immobileTypes = new HashSet<>(Arrays.asList(
			new String[] {
					"null",
					"list",
					"Num",
					"Str",
					"FormattedValue",
					"JoinedStr",
					"Bytes",
					"Ellipsis",
					"NamedConstant",
			}
	));

	@Override
	public boolean canMove(Node node) {
		return node != null && !immobileTypes.contains(node.type());
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

	@Override
	public NodeConstructor getNodeConstructor() {
		return PythonNode::new;
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
