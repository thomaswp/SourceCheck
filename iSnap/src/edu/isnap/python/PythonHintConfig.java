package edu.isnap.python;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import edu.isnap.hint.HintConfig;
import edu.isnap.node.Node;
import edu.isnap.node.Node.NodeConstructor;
import edu.isnap.rating.RatingConfig;

public class PythonHintConfig extends HintConfig {
	private static final long serialVersionUID = 1L;

	public PythonHintConfig() {
		preprocessSolutions = false;
		suggestNewMappedValues = true;
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

	@Override
	public boolean areNodeIDsConsistent() {
		return false;
	}

	private final static Map<String, String> nameMap = new LinkedHashMap<>();
	static {
		nameMap.put("Name", "a variable");
		nameMap.put("BinOp", "a binary operation (e.g. + or *)");
		nameMap.put("Compare", "a comparisong (e.g. a = b or a < b)");
		nameMap.put("Call", "a function call (e.g. len() or max())");
		nameMap.put("Subscript", "a list with a subscript (e.g. x[1])");
		nameMap.put("Index", "a subscript for a list (e.g. x[1])");
		nameMap.put("Assign", "a variable assignment (e.g. x = 5)");
		nameMap.put("AugAssign", "a variable increment/decrement (e.g. x += 5)");
		nameMap.put("Import", "an import statement (e.g. import math");
		nameMap.put("ImportFrom", "an import statement (e.g. from math import sin)");
		nameMap.put("Num", "a number literal");
		nameMap.put("Str", "a string literal");
		nameMap.put("If", "an if statement");
		nameMap.put("For", "a for loop");
		nameMap.put("While", "a while loop");
		nameMap.put("Break", "a break statement");
		nameMap.put("Continue", "a continue statement");
		nameMap.put("Return", "a return statement");
	}

	@Override
	public String getHumanReadableName(Node node) {
		if (node == null) return null;
		if (node.hasType("UnaryOp")) {
			if (node.search(new Node.TypePredicate("USub")) != null) {
				return "a negative value (e.g. -1)";
			} else if (node.search(new Node.TypePredicate("Not")) != null) {
				return "a not operator (e.g. not x)";
			}
		}
		return nameMap.get(node.type());
	}
}