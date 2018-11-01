package edu.isnap.eval.predict;

import java.util.Map;

import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.HintConfig;
import edu.isnap.node.Node;
import edu.isnap.util.Spreadsheet;

public interface AttributeGenerator {
	void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config);
	void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt, Node node);
	String getName();
}
