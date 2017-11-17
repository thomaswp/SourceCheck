package edu.isnap.eval.python;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;

public class PythonConfig extends HintConfig {
	private static final long serialVersionUID = 1L;

	public PythonConfig() {
		script = "list";

		// TODO: make set
		literal = "Num,String";

		valueMappedTypes = new String[][] {
				new String[] { "Name", "arg" },
				new String[] { "FunctionDef" },
		};
	}

	@Override
	public boolean isCodeElement(Node node) {
		return node != null && !node.hasType(script) &&
				node.hasAncestor(new Node.TypePredicate(script));
	}
}
