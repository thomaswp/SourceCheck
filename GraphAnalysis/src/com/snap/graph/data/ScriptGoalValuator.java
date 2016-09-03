package com.snap.graph.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.snap.util.CountMap;
import com.snap.util.ListMap;

public class ScriptGoalValuator {

	// The median count of each item across goals
	private final CountMap<String> medianCounts;
	// For every item in the script, a list of the items that come before it
	// in each of the provided goals. This will include multiple orderings per goal, since
	// an item may appear multiple times in a goal
	private final ListMap<String, CountMap<String>> orderings;

	public ScriptGoalValuator(CountMap<VectorState> goalCounts) {

		medianCounts = new CountMap<>();
		orderings = new ListMap<>();

		int totalGoals = goalCounts.totalCount();
		if (totalGoals == 0) return;

		// Get a list of how many times each item appears in each goal
		ListMap<String, Integer> itemCountLists = new ListMap<>();
		for (VectorState state : goalCounts.keySet()) {
			CountMap<String> stateItems = CountMap.fromArray(state.items);
			for (String item : stateItems.keySet()) {
				int itemCount = stateItems.getCount(item);
				int stateCount = goalCounts.getCount(state);
				for (int i = 0; i < stateCount; i++) {
					itemCountLists.add(item, itemCount);
				}
			}
		}

		// Calculate the median counts
		for (String item : itemCountLists.keySet()) {
			List<Integer> list = itemCountLists.get(item);
			// The list of counts should be padded to have a 0 for each goal not counted
			while (list.size() < totalGoals) list.add(0);
			Collections.sort(list);
			medianCounts.put(item, list.get(list.size() / 2));
		}
		// We only include items with median > 0 count across goals
		medianCounts.removeZeros();

		// Caculate the orderings
		// For every item that we want to include in the script
		for (String item : medianCounts.keySet()) {
			// Go through the goals in our list
			for (VectorState state : goalCounts.keySet()) {
				// Go through every item in each goal
				for (int i = 0; i < state.length(); i++) {
					String stateItem = state.items[i];
					// If that item should be included in the script...
					if (stateItem.equals(item)) {
						// Get the items that came before it and store them as as Count Map
						String[] previousItems = Arrays.copyOf(state.items, i);
						CountMap<String> counts = CountMap.fromArray(previousItems);
						int stateCount = goalCounts.getCount(state);
						for (int j = 0; j < stateCount; j++) {
							orderings.add(item, counts);
						}
					}
				}
			}
		}
	}

	public double getGoalValue(VectorState state) {
		double countDistance = getGoalCountDistance(state);
		double orderDistance = getGoalOrderDistance(state);

		// TODO: Make these more justified/configurable
		double score1 = 1 / Math.pow(countDistance + (1.0 / 10), 2); // max 100
		double score2 = 1 / Math.pow(orderDistance + (1.0 / 7), 2); // max 49
		return (score1 + score2) / 1.49; // max 100
	}

	private double getGoalCountDistance(VectorState state) {
		return CountMap.distance(CountMap.fromArray(state.items), medianCounts);
	}

	private double getGoalOrderDistance(VectorState state) {
		double distance = 0;
		double missPenalty = CountMap.distance(medianCounts, new CountMap<String>());
		int count = 0;
		for (int i = 0; i < state.length(); i++) {
			String stateItem = state.items[i];
			if (orderings.containsKey(stateItem)) {
				String[] previousItems = Arrays.copyOf(state.items, i);
				CountMap<String> counts = CountMap.fromArray(previousItems);
				List<CountMap<String>> orders = orderings.get(stateItem);

				List<Double> distances = new LinkedList<>();
				for (CountMap<String> order : orders) {
					distances.add(CountMap.distance(order, counts));
				}
				Collections.sort(distances);

				int index = distances.size() / medianCounts.getCount(stateItem) / 2;
				distance += distances.get(index);
			} else {
				distance += missPenalty;
			}
			count++;
		}
		if (count != 0) {
			distance /= count;
		}
		return distance;
	}

}
