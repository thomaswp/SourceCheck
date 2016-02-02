package pqgram;

import java.util.Arrays;
import java.util.HashSet;

import astrecognition.model.Graph;
import astrecognition.model.Tree;

/**
 * Computes pq-Gram distance (adapted from
 * http://www.vldb2005.org/program/paper/wed/p301-augsten.pdf)
 */
public class PQGram {
	public static String STAR_LABEL = "*";
	private static Graph NULL_GRAPH = new Tree(STAR_LABEL); 

	public static double getDistance(Graph g, Graph h, int p, int q) {
		Profile profile = PQGram.getProfile(g, p, q);
		Profile profile2 = PQGram.getProfile(h, p, q);
		Profile union = profile.union(profile2);
		Profile intersection = profile.intersect(profile2);
		return 1 - (2.0 * intersection.size()) / union.size();
	}

	public static Profile getProfile(Graph t, int p, int q) {
		Graph[] stem = new Graph[p];
		Arrays.fill(stem, NULL_GRAPH);
		return getLabelTuples(p, q, new Profile(), t, stem, new HashSet<Graph>());
	}

	private static Profile getLabelTuples(int p, int q, Profile profile,
			Graph a, Graph[] stem, HashSet<Graph> visited) {
		if (visited.contains(a)) {
			return profile;
		}

		visited.add(a);

		Graph[] base = new Graph[q];
		Arrays.fill(base, NULL_GRAPH);
		stem = shift(stem, a);
		if (a.isSink()) {
			profile.add(concatenate(stem, base));
		} else {
			for (Graph c : a.getConnections()) {
				base = shift(base, c);
				profile.add(concatenate(stem, base));
				profile = getLabelTuples(p, q, profile, c, stem, visited);
			}
			for (int k = 1; k < q; k++) {
				base = shift(base, NULL_GRAPH);
				profile.add(concatenate(stem, base));
			}
		}
		return profile;
	}

	private static Graph[] concatenate(Graph[] stem, Graph[] base) {
		Graph[] result = new Graph[stem.length + base.length];
		for (int i = 0; i < stem.length; i++) {
			result[i] = stem[i];
		}
		for (int i = stem.length; i < result.length; i++) {
			result[i] = base[i - stem.length];
		}
		return result;
	}

	private static Graph[] shift(Graph[] arr, Graph graph) {
		Graph[] newArr = new Graph[arr.length];
		for (int i = 1; i < arr.length; i++) {
			newArr[i - 1] = arr[i];
		}
		newArr[arr.length - 1] = graph;
		return newArr;
	}

}
