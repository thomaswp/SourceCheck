package edu.isnap.eval.viz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.apporiented.algorithm.clustering.AverageLinkageStrategy;
import com.apporiented.algorithm.clustering.Cluster;
import com.apporiented.algorithm.clustering.ClusteringAlgorithm;
import com.apporiented.algorithm.clustering.DefaultClusteringAlgorithm;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.eval.export.ClusterDistance;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class HierarchicalCluster {

	public static void main(String[] args) {
		new HierarchicalCluster().go();
	}

	private Map<String, Node> labelMap;
	private Map<Node, Integer> indexMap;
	private double[][] distances;

	private void go() {
		Assignment assignment = CSC200.GuessingGame1;
		Node[] submitted = assignment.load(Mode.Use, true, true,
				new SnapParser.LikelySubmittedOnly()).values().stream()
				.filter(a -> a.submittedSnapshot != null)
				.map(a -> SimpleNodeBuilder.toTree(a.submittedSnapshot, true))
				.toArray(Node[]::new);

		int n = submitted.length;
		labelMap = Arrays.stream(submitted).collect(Collectors.toMap(
				a -> ((Snapshot) a.tag).name.substring(0, 5),
				a -> a));
		String[] names = labelMap.keySet().toArray(new String[n]);
		indexMap = IntStream.range(0, n).boxed().collect(Collectors.toMap(
				i -> labelMap.get(names[i]),
				i -> i,
				(i, j) -> i,
				() -> new IdentityHashMap<>()));
		distances = new double[n][n];

		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				double dis = ClusterDistance.EditCountMeasure.measure(
						submitted[i], submitted[j]);
				distances[i][j] = distances[j][i] = dis;
			}
		}

		// Print the distribution and mean of nearest neighbor distances
		double[] minDistances = new double[n];
		for (int i = 0; i < n; i++) {
			double min = Double.MAX_VALUE;
			for (int j = 0; j < n; j++) {
				if (i != j) min = Math.min(min, distances[i][j]);
			}
			minDistances[i] = min;
		}
		System.out.println(Arrays.stream(minDistances).average().getAsDouble());
		System.out.println(Arrays.toString(minDistances));

		ClusteringAlgorithm alg = new DefaultClusteringAlgorithm();
		Cluster cluster = alg.performClustering(distances, names,
				new AverageLinkageStrategy());

		ListMap<Cluster, Node> leafMap = new ListMap<>();
		getLeaves(cluster, leafMap);
		Map<Cluster, Node> medoids = leafMap.keySet().stream().collect(Collectors.toMap(
				c -> c,
				c -> medoid(getLeaves(c, leafMap))));


		Scanner sc = new Scanner(System.in);
		while (true) {
			String parent = medoids.get(cluster).prettyPrint();
			System.out.println(parent);
			System.out.printf("Children (%d): \n", cluster.countLeafs());
			for (int i = 1; i <= cluster.getChildren().size(); i++) {
				Cluster child = cluster.getChildren().get(i-1);
				System.out.printf("#%d (%d): ", i, child.countLeafs());
				String diff = Diff.diff(parent, medoids.get(child).prettyPrint());
				System.out.println(diff);
			}
			int in = sc.nextInt();
			if (in == 0) {
				cluster = cluster.getParent();
			} else if (in < 0) {
				break;
			} else {
				cluster = cluster.getChildren().get(in-1);
			}
			System.out.println("\n====================================\n");
		}
		sc.close();
	}

	private List<Node> getLeaves(Cluster cluster, ListMap<Cluster, Node> leafMap) {
		if (leafMap.containsKey(cluster)) return leafMap.get(cluster);

		List<Node> leaves = new ArrayList<>();
		if (cluster.isLeaf()) {
			leaves.add(labelMap.get(cluster.getName()));
		} else {
			for (Cluster child : cluster.getChildren()) {
				leaves.addAll(getLeaves(child, leafMap));
			}
		}
		leafMap.put(cluster, leaves);
		return leaves;
	}

	private Node medoid(List<Node> cluster) {
		double minDis = Double.MAX_VALUE;
		Node medoid = null;
		for (Node node : cluster) {
			int nodeIndex = indexMap.get(node);
			double dis = cluster.stream().map(n -> distances[nodeIndex][indexMap.get(n)]).collect(
					Collectors.summingDouble(d -> d));
			if (dis < minDis) {
				minDis = dis;
				medoid = node;
			}
		}
		return medoid;

	}

}
