package edu.isnap.ctd.hint.feature;

import java.util.Set;

public class PQGramRule extends CodeShapeRule implements Comparable<PQGramRule> {

	public final PQGram pqGram;

	@SuppressWarnings("unused")
	private PQGramRule() {
		this(null, 0);
	}

	public PQGramRule(PQGram pqGram, int maxFollowers) {
		super(maxFollowers);
		this.pqGram = pqGram;
	}

	@Override
	public String toString() {
		return String.format("%.02f: %s", support(), pqGram);
	}

	@Override
	public int compareTo(PQGramRule o) {
		int fc = Integer.compare(followCount(), o.followCount());
		if (fc != 0) return fc;
		return pqGram.compareTo(o.pqGram);
	}

	@Override
	public boolean isSatisfied(Set<PQGram> pqGrams) {
		return pqGrams.contains(pqGram);
	}
}