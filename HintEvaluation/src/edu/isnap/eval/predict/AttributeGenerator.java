package edu.isnap.eval.predict;

import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.util.Spreadsheet;

public interface AttributeGenerator {
	void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config);
	void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt, Node node);
	String getName();
}
