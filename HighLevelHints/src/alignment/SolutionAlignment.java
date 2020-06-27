package alignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.util.Alignment;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.NodeAlignment;

public class SolutionAlignment extends Alignment {
	
	public static double getFullOrderReward(int length, double baseReward, double factor) {
		double reward = 0.0;
		for (int i = 0; i < length; i++) {
			reward += Math.pow(baseReward, Math.pow(factor, i));
		}
		return reward;
	}
	
	public static int getMissingNodeCount(String[] from, String[] to) {
		return (int) (to.length - getProgress(to, from, 1, 1, true));
	}
	
//	public static double getProgress(String[] from, String[] to, int orderReward, int unorderReward) {
//		return getProgress(from, to, orderReward, unorderReward, false);
//	}
	
	public static double getProgress(String[] from, String[] to, int[] toOrderGroups, int orderReward, int unorderReward) {
		return getProgress(from, to, toOrderGroups, orderReward, unorderReward, new int[to.length], false);
	}
	
	private static double getProgress(String[] from, String[] to, int orderReward, 
			int unorderReward, boolean missing) {
		return getProgress(from, to, null, orderReward, unorderReward, new int[to.length], missing);
	}
	
	private static double getProgress(String[] from, String[] to, int[] toOrderGroups,
			int orderReward, int unorderReward, int[] toIndices, boolean missing) {
		// TODO: This can and should be much more efficient
		// toList[k] = "\0" if the k-th node in c(b_{rj}) is in c(a_{ri})
		List<String> toList = new LinkedList<>(Arrays.asList(to));

		// If indices[k] = l, k-th node in c(a_{ri}) is l-th node in c(b_{rj})
		// If indices[k] = -1, k-th node in c(a_{ri}) is not in c(b_{rj})
		//int[] indices = new int[from.length];
		List<Integer> indices  = new ArrayList<>(from.length);
		for (int i = 0; i < from.length; i++) {
			String item = from[i];
			int index = toList.indexOf(item);
			if (index >= 0) { // i-th node in c(a_{ri}) is in c(b_{rj})
				if (missing) {
					toList.set(index, "\0"); // prevents multiple matches
				}
				//indices[i] = index;
				indices.add(i, index);
			} else {
				//indices[i] = -1;
				indices.add(i, index);
			}
		}

		Arrays.fill(toIndices, -1);
		
		Queue<List<Integer>> oneToOneIndices = new LinkedList<>();
		oneToOneIndices.add(indices);
		if (!missing) {
			for (int i = 0; i < to.length; i++) {
				List<Integer> fromIndices = new ArrayList<>();
				List<Integer> example = oneToOneIndices.peek();
				for (int j = 0; j < example.size(); j++) {
					if (example.get(j) == i) {
						fromIndices.add(j);
					}
				}
				if (fromIndices.size() < 2) {
					continue; // no duplicates
				}
				// Multiple elements in "from" point to one element in "to"
				int queueSize = oneToOneIndices.size();
				for (int j = 0; j < queueSize; j++) {
					ArrayList<Integer> polled = (ArrayList<Integer>) oneToOneIndices.poll();
					for (int index : fromIndices) {
						List<Integer> temp = new ArrayList<>(polled.size());
						for (int h = 0; h < polled.size(); h++) {
							if (fromIndices.indexOf(polled.get(h)) != -1 && h != index) {
								temp.add(h, -1);
							}else {
								temp.add(h, polled.get(h));
							}
						}
						oneToOneIndices.add(new ArrayList<Integer>(temp));
					}
				}
			}
		}
		
		double maxReward = Double.NEGATIVE_INFINITY;
		for (List<Integer> filteredIndices : oneToOneIndices) {

		double reward = 0;
		int lastIndex = -1;
		int maxIndex = -1;
		for (Integer index : filteredIndices) {
			if (index < 0) continue; // don't change reward if k-th node in c(a_{ri}) is not in c(b_{rj})
			int adjIndex = index;
			int group;
//			System.out.println(index);
			if (toOrderGroups != null && (group = toOrderGroups[adjIndex]) > 0) { // always false for Java SourceCheck
				// If the matched "to" item is in an order group (for which all items in the group
				// are unordered), we should match i to the index of the earliest item in this group
				// which comes after the last index, since the actual match could have been
				// reordered to that index without loss of meaning

				// First check if the index can be decreased within the order group without going
				// <= the max seen index (to avoid duplicate adjusted indices)
				while (adjIndex > 0 && adjIndex - 1 > maxIndex &&
						toOrderGroups[adjIndex - 1] == group) {
					adjIndex--;
//					System.out.println("m-> " + adjIndex);
				}
				// Next check if the index is out of order and increasing it to maxIndex + 1 will
				// make in order
				int nextIndex = maxIndex + 1;
				if (nextIndex < toOrderGroups.length && adjIndex <= lastIndex  &&
						toOrderGroups[nextIndex] == group) {
					adjIndex = nextIndex;
//					System.out.println("p-> " + adjIndex);
				}

				// Set the actual to-index used after adjustments above
				if (index != adjIndex) {
					toIndices[index] = adjIndex;
				}
			}

			if (to[adjIndex] != null) {
				reward += adjIndex > lastIndex ? orderReward : unorderReward;
			}
			lastIndex = adjIndex;
			maxIndex = Math.max(maxIndex, adjIndex);
		}
		if (reward > maxReward) maxReward = reward;
		}

		return maxReward;
	}
	
	public static double getNormalizedMissingNodeCount(String[] from, String[] to, 
			double baseReward, double factor) {
		return getFullOrderReward(to.length, baseReward, factor) 
				- getNormalizedProgress(to, from, null, baseReward, 1, factor, new int[to.length]);
	}
	
	public static double getNormalizedProgress(String[] from, String[] to, int[] toOrderGroups,
			double baseReward, double unorderReward, double factor, int[] toIndices) {
		// TODO: This can and should be much more efficient
		// toList[k] = "\0" if the k-th node in c(b_{rj}) is in c(a_{ri})
		List<String> toList = new LinkedList<>(Arrays.asList(to));

		// If indices[k] = l, k-th node in c(a_{ri}) is l-th node in c(b_{rj})
		// If indices[k] = -1, k-th node in c(a_{ri}) is not in c(b_{rj})
		int[] indices = new int[from.length];
		for (int i = 0; i < from.length; i++) {
			String item = from[i];
			int index = toList.indexOf(item);
			if (index >= 0) { // i-th node in c(a_{ri}) is in c(b_{rj})
				toList.set(index, "\0");
				indices[i] = index;
			} else {
				indices[i] = -1;
			}
		}

		Arrays.fill(toIndices, -1);

		double reward = 0;
		int lastIndex = -1;
		int maxIndex = -1;
		for (Integer index : indices) {
			if (index < 0) continue; // don't change reward if k-th node in c(a_{ri}) is not in c(b_{rj})
			int adjIndex = index;
			int group;
//			System.out.println(index);
			if (toOrderGroups != null && (group = toOrderGroups[adjIndex]) > 0) { // always false for Java SourceCheck
				// If the matched "to" item is in an order group (for which all items in the group
				// are unordered), we should match i to the index of the earliest item in this group
				// which comes after the last index, since the actual match could have been
				// reordered to that index without loss of meaning

				// First check if the index can be decreased within the order group without going
				// <= the max seen index (to avoid duplicate adjusted indices)
				while (adjIndex > 0 && adjIndex - 1 > maxIndex &&
						toOrderGroups[adjIndex - 1] == group) {
					adjIndex--;
//					System.out.println("m-> " + adjIndex);
				}
				// Next check if the index is out of order and increasing it to maxIndex + 1 will
				// make in order
				int nextIndex = maxIndex + 1;
				if (nextIndex < toOrderGroups.length && adjIndex <= lastIndex  &&
						toOrderGroups[nextIndex] == group) {
					adjIndex = nextIndex;
//					System.out.println("p-> " + adjIndex);
				}

				// Set the actual to-index used after adjustments above
				if (index != adjIndex) {
					toIndices[index] = adjIndex;
				}
			}

			if (to[adjIndex] != null) {
				reward += adjIndex > lastIndex ? Math.pow(baseReward, Math.pow(factor, adjIndex)) : 
					Math.pow(unorderReward, Math.pow(factor, adjIndex));
			}
			lastIndex = adjIndex;
			maxIndex = Math.max(maxIndex, adjIndex);
		}

		return reward;
	}
	
}
