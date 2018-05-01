package edu.isnap.eval.python;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.NodeConstructor;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.eval.python.PythonImport.PythonNode;
import edu.isnap.rating.RatingConfig;

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
		return node != null && RatingConfig.Python.hasFixedChildren(node.type(), node.parentType());
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
					"Load",
					"Store",
					"Del",
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
				(parent.hasType("Call") && index == 0) ||
				// Children of auto-added lists (e.g. in compare) should also be auto-added
				(parent.hasType("list") && shouldAutoAdd(parent)) ||
				node.hasType("Load", "Store", "Del");
	}

	@Override
	public NodeConstructor getNodeConstructor() {
		return PythonNode::new;
	}
}
