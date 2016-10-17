package com.snap.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Credit: Java ML Library
 *
 * Implementation of the K-medoids algorithm. K-medoids is a clustering
 * algorithm that is very much like k-means. The main difference between the two
 * algorithms is the cluster center they use. K-means uses the average of all
 * instances in a cluster, while k-medoids uses the instance that is the closest
 * to the mean, i.e. the most 'central' point of the cluster.
 *
 * Using an actual point of the data set to cluster makes the k-medoids
 * algorithm more robust to outliers than the k-means algorithm.
 *
 *
 * @author Thomas Abeel
 *
 */
public class KMedoids<T> {
	/* Distance measure to measure the distance between instances */
	private DistanceMeasure<T> dm;

	/* Number of clusters to generate */
	private int numberOfClusters;

	/* Random generator for selection of candidate medoids */
	private Random rg;

	/* The maximum number of iterations the algorithm is allowed to run. */
	private int maxIterations;

	/**
	 * Creates a new instance of the k-medoids algorithm with the specified
	 * parameters.
	 *
	 * @param numberOfClusters
	 *            the number of clusters to generate
	 * @param maxIterations
	 *            the maximum number of iteration the algorithm is allowed to
	 *            run
	 * @param DistanceMeasure
	 *            dm the distance metric to use for measuring the distance
	 *            between instances
	 *
	 */
	public KMedoids(int numberOfClusters, int maxIterations, DistanceMeasure<T> dm) {
		super();
		this.numberOfClusters = numberOfClusters;
		this.maxIterations = maxIterations;
		this.dm = dm;
		rg = new Random(System.currentTimeMillis());
	}

	public List<List<T>> cluster(List<T> data) {
		List<T> medoids = new LinkedList<>();
		List<List<T>> output = new LinkedList<>();
		for (int i = 0; i < numberOfClusters; i++) {
			int random = rg.nextInt(data.size());
			medoids.add(data.get(random));
			output.add(new LinkedList<T>());
		}

		boolean changed = true;
		int count = 0;
		while (changed && count++ < maxIterations) {
			int[] assignment = assign(medoids, data);
			changed = recalculateMedoids(assignment, medoids, output, data);

		}

		return output;

	}

	/**
	 * Assign all instances from the data set to the medoids.
	 *
	 * @param medoids candidate medoids
	 * @param data the data to assign to the medoids
	 * @return best cluster indices for each instance in the data set
	 */
	private int[] assign(List<T> medoids, List<T> data) {
		int[] out = new int[data.size()];
		for (int i = 0; i < data.size(); i++) {
			double bestDistance = dm.measure(data.get(i), medoids.get(0));
			int bestIndex = 0;
			for (int j = 1; j < medoids.size(); j++) {
				double tmpDistance = dm.measure(data.get(i), medoids.get(j));
				if (tmpDistance < bestDistance) {
					bestDistance = tmpDistance;
					bestIndex = j;
				}
			}
			out[i] = bestIndex;

		}
		return out;

	}

	/**
	 * Return a array with on each position the clusterIndex to which the
	 * Instance on that position in the dataset belongs.
	 *
	 * @param medoids
	 *            the current set of cluster medoids, will be modified to fit
	 *            the new assignment
	 * @param assigment
	 *            the new assignment of all instances to the different medoids
	 * @param output
	 *            the cluster output, this will be modified at the end of the
	 *            method
	 * @return the
	 */
	private boolean recalculateMedoids(int[] assignment, List<T> medoids,
			List<List<T>> output, List<T> data) {
		boolean changed = false;
		for (int i = 0; i < numberOfClusters; i++) {
			List<T> cluster = output.get(i);
			cluster.clear();
			for (int j = 0; j < assignment.length; j++) {
				if (assignment[j] == i) {
					cluster.add(data.get(j));
				}
			}
			if (cluster.size() == 0) { // new random, empty medoid
				medoids.set(i, data.get(rg.nextInt(data.size())));
				changed = true;
			} else {
				T medoid = medoids.get(i);
				double bestDistance = totalDistance(cluster, medoid);
				for (T possibleMedoid : cluster) {
					if (possibleMedoid == medoid) continue;
					double distance = totalDistance(cluster, possibleMedoid);
					if (distance < bestDistance) {
						bestDistance = distance;
						medoid = possibleMedoid;
						changed = true;
					}
				}
				medoids.set(i, medoid);
			}
		}
		return changed;
	}


	private double totalDistance(List<T> cluster, T medoid) {
		double distance = 0;
		for (T t : cluster) {
			distance += dm.measure(t, medoid);
		}
		return distance;
	}

	public interface DistanceMeasure<T> {
		double measure(T a, T b);
	}

	public static void main(String[] args) {
		int size = 100;
		int max = 300;
		List<Integer> data = new LinkedList<>();
		for (int i = 0; i < size; i++) {
			data.add((int) (Math.random() * max));
		}
		KMedoids<Integer> km = new KMedoids<>(8, 1000, new DistanceMeasure<Integer>() {
			@Override
			public double measure(Integer a, Integer b) {
				return Math.abs(a - b);
			}
		});

		List<List<Integer>> clusters = km.cluster(data);
		System.out.println("Clusters:");
		for (int i = 0; i < clusters.size(); i++) {
			System.out.println(i + ": " + clusters.get(i));
		}
	}
}
