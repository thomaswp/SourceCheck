package edu.isnap.ctd.hint.edit;

import java.util.OptionalDouble;

public class Priority {
	public int consensusNumerator, consensusDenominator;
	public OptionalDouble creationTime = OptionalDouble.empty();
	public OptionalDouble meanOrderingRank = OptionalDouble.empty();
	public int prereqsNumerator, prereqsDenominator;

	public double consensus() {
		return (double) consensusNumerator / consensusDenominator;
	}

	public double prereqs() {
		return (double) prereqsNumerator / prereqsDenominator;
	}

	@Override
	public String toString() {
		String out = String.format("{Consensus: %d/%d=%.02f",
				consensusNumerator, consensusDenominator, consensus());
		if (prereqsDenominator > 0) {
			out += String.format(", Prereqs: %s/%s=%.02f",
					prereqsNumerator, prereqsDenominator, prereqs());
		}
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
