package edu.isnap.hint;

import java.util.List;

import edu.isnap.node.Node;

public interface IDataModel {
	void addTrace(String id, List<Node> trace);
	void finished();
}
