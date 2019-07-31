package edu.isnap.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Feature {

	public final List<CodeShapeRule> rules = new ArrayList<>();
	public final int id;

	@SuppressWarnings("unused")
	private Feature() {
		this(null, 0);
	}

	public Feature(CodeShapeRule startRule, int id) {
		this.id = id;
		addRule(startRule);
	}

	public void addRule(CodeShapeRule rule) {
		rules.add(rule);
	}

	@Override
	public String toString() {
		return String.format("%02d:\t%s", id, String.join(" AND\n\t",
				rules.stream()
				.map(r -> (CharSequence) r.toString().replaceAll("\\s+", " "))::iterator));
	}

	public boolean isSatisfied(Set<PQGram> pqGrams) {
		return rules.stream().allMatch(rule -> rule.isSatisfied(pqGrams));
	}
}