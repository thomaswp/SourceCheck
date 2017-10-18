package edu.isnap.ctd.hint.edit;

public class Priority {
	public int consensusNumerator, consensusDemonimator;

	public double consensus() {
		return (double) consensusNumerator / consensusDemonimator;
	}

	@Override
	public String toString() {
		return String.format("{Consensus: %d/%d=%.02f}",
				consensusNumerator, consensusDemonimator, consensus());
	}
}
