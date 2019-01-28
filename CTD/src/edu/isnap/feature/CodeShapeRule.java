package edu.isnap.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class CodeShapeRule {
	public int index;
	public double orderSD;
	public final Set<String> followers = new HashSet<>();
	public final int maxFollowers;

	public final List<CodeShapeRule> duplicateRules = new ArrayList<>();

	public transient byte[] snapshotVector;
	public transient int snapshotCount;

	public abstract boolean isSatisfied(Set<PQGram> pqGrams);

	public CodeShapeRule(int maxFollowers) {
		this.maxFollowers = maxFollowers;
	}

	public void calculateSnapshotCount() {
		int count = 0;
		for (int j = 0; j < snapshotVector.length; j++) {
			count += snapshotVector[j];
		}
		snapshotCount = count;
	}

	public int followCount() {
		return followers.size();
	}

	public double support() {
		return (double) followers.size() / maxFollowers;
	}

	public int countIntersect(CodeShapeRule rule) {
		return (int) followers.stream().filter(rule.followers::contains).count();
	}

	public int countUnion(CodeShapeRule rule) {
		return (int) Stream.concat(followers.stream(), rule.followers.stream())
				.distinct().count();
	}

	public double jaccardDistance(CodeShapeRule rule) {
		int intersect = countIntersect(rule);
		return (double)intersect / (followCount() + rule.followCount() - intersect);
	}


	public static double jaccardDistance(CodeShapeRule ruleA, CodeShapeRule ruleB) {
		byte[] fA = ruleA.snapshotVector;
		byte[] fB = ruleB.snapshotVector;
		int intersect = 0;
		for (int k = 0; k < fA.length; k++) {
			if (fA[k] == 1 && fB[k] == 1) intersect++;
		}
		double value = (double)intersect /
				(ruleA.snapshotCount + ruleB.snapshotCount - intersect);
		return value;
	}
}