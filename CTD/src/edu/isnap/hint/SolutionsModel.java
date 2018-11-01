package edu.isnap.hint;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.isnap.node.Node;
import edu.isnap.util.map.CountMap;

public class SolutionsModel implements IDataModel {

	private final CountMap<Node> solutions = new CountMap<>();

	@Override
	public void addTrace(String id, List<Node> trace) {
		if (trace.size() > 0) {
			solutions.increment(trace.get(trace.size() - 1));
		}
	}

	public Set<Node> getSolutions() {
		return Collections.unmodifiableSet(solutions.keySet());
	}

	public int getSolutionCount(Node solution) {
		return solutions.get(solution);
	}

	@Override
	public void finished() {

	}

}
