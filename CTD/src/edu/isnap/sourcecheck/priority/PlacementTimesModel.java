package edu.isnap.sourcecheck.priority;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.hint.IDataModel;
import edu.isnap.node.Node;

public class PlacementTimesModel implements IDataModel {

	public final Map<String, Map<String, Double>> nodePlacementTimes = new IdentityHashMap<>();

	@Override
	public void addTrace(String id, List<Node> currentHistory) {
		if (currentHistory.size() == 0) return;

		Map<String, Double> currentNodeCreationPercs = new HashMap<>();
		Node solution = currentHistory.get(currentHistory.size() - 1);
		solution.recurse(item -> {
			if (item.id == null) return;
			String rootPath = item.rootPathString();
			for (int i = 0; i < currentHistory.size(); i++) {
				// If this node has an ID, look for the first time a node with same ID has the
				// same root path in the history, and declare that as its placement time perc
				Node node = currentHistory.get(i);
				Node match = node.searchForNodeWithID(item.id);
				if (match == null) continue;
				if (rootPath.equals(match.rootPathString())) {
					currentNodeCreationPercs.put(item.id,
							(double) i / currentHistory.size());
					break;
				}
			}
		});
		// Then save the current node creation percs, using the final solution as a key
		nodePlacementTimes.put(id, currentNodeCreationPercs);
	}

	@Override
	public void finished() {

	}

}
