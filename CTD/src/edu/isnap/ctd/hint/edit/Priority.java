package edu.isnap.ctd.hint.edit;

import java.util.OptionalDouble;

public class Priority {
	public int consensusNumerator, consensusDemonimator;
	public OptionalDouble creationTime = OptionalDouble.empty();
	public OptionalDouble meanOrderingRank = OptionalDouble.empty();

	public double consensus() {
		return (double) consensusNumerator / consensusDemonimator;
	}

	@Override
	public String toString() {
		String out = String.format("{Consensus: %d/%d=%.02f",
				consensusNumerator, consensusDemonimator, consensus());
		if (creationTime.isPresent()) {
			out += String.format(", Creation: %.02f", creationTime.getAsDouble());
		}
		if (meanOrderingRank.isPresent()) {
			out += String.format(", Order: %.02f", meanOrderingRank.getAsDouble());
		}
		out += "}";
		return out;
	}
}
