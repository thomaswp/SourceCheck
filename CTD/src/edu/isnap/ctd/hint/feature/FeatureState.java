package edu.isnap.ctd.hint.feature;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.StringHashable;

public class FeatureState extends StringHashable {

	public final boolean[] featuresPresent;

	public FeatureState(Set<Feature> featureSet, int nFeatures) {
		featuresPresent = new boolean[nFeatures];
		for (Feature feature : featureSet) featuresPresent[feature.id - 1] = true;
		cache();
	}

	public FeatureState(Node node, List<Feature> allFeatures) {
		featuresPresent = new boolean[allFeatures.size()];

		Set<PQGram> pqGrams = PQGram.extractAllFromNode(node);
		allFeatures.stream()
		.filter(feature -> feature.isSatisfied(pqGrams))
		.forEach(feature -> featuresPresent[feature.id - 1] = true);
	}

	public FeatureState(boolean[] featuresPresent) {
		this.featuresPresent = Arrays.copyOf(featuresPresent, featuresPresent.length);
	}

	public FeatureState plus(FeatureState state) {
		boolean[] features = new boolean[featuresPresent.length];
		for (int i = 0; i < featuresPresent.length; i++) {
			features[i] = featuresPresent[i] || state.featuresPresent[i];
		}
		return new FeatureState(features);
	}

	@Override
	protected String toCanonicalStringInternal() {
		StringBuilder sb = new StringBuilder();
		for (boolean f : featuresPresent) sb.append(f ? "1" : "0");
		return sb.toString();
	}

	public static FeatureState empty(int size) {
		return new FeatureState(new HashSet<>(), size);
	}

	public static int matchCount(FeatureState a, FeatureState b) {
		if (a.featuresPresent.length != b.featuresPresent.length) return 0;
		int match = 0;
		for (int i = 0; i < a.featuresPresent.length; i++) {
			if (a.featuresPresent[i] == b.featuresPresent[i]) match++;
		}
		return match;
	}

	public static double similarity(FeatureState a, FeatureState b) {
		return (double) matchCount(a, b) / a.featuresPresent.length;
	}
}
