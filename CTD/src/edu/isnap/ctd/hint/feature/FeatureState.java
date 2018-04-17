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

	@Override
	protected String toCanonicalStringInternal() {
		return Arrays.toString(featuresPresent);
	}

	public static FeatureState empty(int size) {
		return new FeatureState(new HashSet<>(), size);
	}
}
