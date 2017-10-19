package edu.isnap.ctd.hint.edit;

public class Priority {
	public int consensusNumerator, consensusDemonimator;
	public Double creationPerc;

	public double consensus() {
		return (double) consensusNumerator / consensusDemonimator;
	}

	@Override
	public String toString() {
		String out = String.format("{Consensus: %d/%d=%.02f",
				consensusNumerator, consensusDemonimator, consensus());
		if (creationPerc != null) {
			out += String.format(", Creation: %.02f}", creationPerc);
		}
		out += "}";
		return out;
	}
}
