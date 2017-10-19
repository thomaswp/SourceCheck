package edu.isnap.ctd.hint.edit;

import java.util.OptionalDouble;

public class Priority {
	public int consensusNumerator, consensusDemonimator;
	public OptionalDouble creationPerc = OptionalDouble.empty();

	public double consensus() {
		return (double) consensusNumerator / consensusDemonimator;
	}

	@Override
	public String toString() {
		String out = String.format("{Consensus: %d/%d=%.02f",
				consensusNumerator, consensusDemonimator, consensus());
		if (creationPerc.isPresent()) {
			out += String.format(", Creation: %.02f}", creationPerc.getAsDouble());
		}
		out += "}";
		return out;
	}
}
