package edu.isnap.sourcecheck.priority;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

public class Priority {
	public int consensusNumerator, consensusDenominator;
	public OptionalDouble creationTime = OptionalDouble.empty();
	public int prereqsNumerator, prereqsDenominator;
	public int postreqsNumerator, postreqsDenominator;
	public int orderingNumerator, orderingDenominator;

	public double consensus() {
		return (double) consensusNumerator / consensusDenominator;
	}

	public double prereqs() {
		return (double) prereqsNumerator / prereqsDenominator;
	}

	public double postreqs() {
		return (double) postreqsNumerator / postreqsDenominator;
	}

	public double ordering() {
		return (double) orderingNumerator / orderingDenominator;
	}

	@Override
	public String toString() {
		String out = String.format("{Consensus: %d/%d=%.02f",
				consensusNumerator, consensusDenominator, consensus());
//		if (prereqsDenominator > 0) {
//			out += String.format(", Prereqs: %s/%s=%.02f",
//					prereqsNumerator, prereqsDenominator, prereqs());
//		}
//		if (postreqsDenominator > 0) {
//			out += String.format(", Postreqs: %s/%s=%.02f",
//					postreqsNumerator, postreqsDenominator, postreqs());
//		}
//		if (orderingDenominator > 0) {
//			out += String.format(", Ordering: %s/%s=%.02f",
//					orderingNumerator, orderingDenominator, ordering());
//		}
//		if (creationTime.isPresent()) {
//			out += String.format(", Creation: %.02f", creationTime.getAsDouble());
//		}
		out += "}";
		return out;
	}

	public Map<String, Object> getPropertiesMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("consensus", consensus());
		map.put("consensusNum", consensusNumerator);
		map.put("consensusDen", consensusDenominator);
		map.put("prereqs", prereqs());
		map.put("prereqsNum", prereqsNumerator);
		map.put("prereqsDen", prereqsDenominator);
		map.put("postreqs", postreqs());
		map.put("postreqsNum", postreqsNumerator);
		map.put("postreqsDen", postreqsDenominator);
		map.put("ordering", ordering());
		map.put("orderingNum", orderingNumerator);
		map.put("orderingDen", orderingDenominator);
		map.put("creation", creationTime.isPresent() ? creationTime.getAsDouble() : null);
		return map;
	}
}
