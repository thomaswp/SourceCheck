package edu.isnap.eval.python;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;

public class PythonConfig extends HintConfig {
	private static final long serialVersionUID = 1L;

	public PythonConfig() {
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
//			new String[] { "Name", "arg" },
			new String[] { "FunctionDef" },
	};

	@Override
	public String[][] getValueMappedTypes() {
		return valueMappedTypes;
	}
}
