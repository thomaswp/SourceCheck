package edu.isnap.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DisjunctionRule extends CodeShapeRule implements Comparable<DisjunctionRule> {

	public final List<PQGramRule> rules = new ArrayList<>();

	private DisjunctionRule() {
		super(0);
	}

	public DisjunctionRule(PQGramRule startRule) {
		super(startRule.maxFollowers);
		snapshotVector = new byte[startRule.snapshotVector.length];
		addRule(startRule);
	}

	public double meanJaccard() {
		int n = rules.size();
		double totalJacc = 0;
		int count = 1;
		for (int i = 0; i < n; i++) {
			PQGramRule ruleA = rules.get(i);
			for (int j = i + 1; j < n; j++) {
				totalJacc += jaccardDistance(ruleA, rules.get(j));
				count++;
			}
		}
		return totalJacc / count;
	}

	public void addRule(PQGramRule rule) {
		rules.add(rule);
		followers.addAll(rule.followers);
		for (int i = 0; i < snapshotVector.length; i++) {
			if (rule.snapshotVector[i] == 1) snapshotVector[i] = 1;
		}
		calculateSnapshotCount();
	}

	@Override
	public String toString() {
		return String.format("%.02f:\t%s", support(), String.join(" OR\n\t\t\t\t",
				rules.stream().map(r -> (CharSequence) r.toString())::iterator));
	}

	@Override
	public int compareTo(DisjunctionRule o) {
		int fc = Integer.compare(followCount(), o.followCount());
		if (fc != 0) return fc;

		int cr = -Integer.compare(rules.size(), o.rules.size());
		if (cr != 0) return cr;

		return rules.stream().max(PQGramRule::compareTo).get().compareTo(
				o.rules.stream().max(PQGramRule::compareTo).get());
	}

	@Override
	public boolean isSatisfied(Set<PQGram> pqGrams) {
		return rules.stream().anyMatch(rule -> rule.isSatisfied(pqGrams));
	}
}