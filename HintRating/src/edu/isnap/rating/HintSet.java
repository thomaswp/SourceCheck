package edu.isnap.rating;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.rating.HintOutcome.HintWithError;
import edu.isnap.rating.RateHints.RatingConfig;

public class HintSet {

	public final String name;
	public final RatingConfig config;

	private final ListMap<Integer, HintOutcome> hintMap = new ListMap<>();

	public HintSet(String name, RatingConfig config) {
		this.name = name;
		this.config = config;
	}

	public void add(HintOutcome outcome) {
		hintMap.add(outcome.snapshotID, outcome);
	}

	public Set<Integer> getHintRequestIDs() {
		return hintMap.keySet();
	}

	public List<HintOutcome> getOutcomes(int snapshotID) {
		return Collections.unmodifiableList(hintMap.get(snapshotID));
	}

	public void finish() {
		for (Integer id : hintMap.keySet()) {
			List<HintWithError> outcomesWithError = hintMap.get(id).stream()
					.filter(HintWithError.class::isInstance)
					.map(HintWithError.class::cast)
					.collect(Collectors.toList());

			DoubleSummaryStatistics errorStats = outcomesWithError.stream()
				.mapToDouble(e -> e.error)
				.summaryStatistics();

			double minError = errorStats.getMin();
			double expectedError = errorStats.getAverage() - minError;
			// Beta is 1 / expectedError, but if expectedError is zero beta is multiplied by 0
			// in the formula, so we simply set it to 1
			double beta = expectedError == 0 ? 1 : 1 / (expectedError);

			outcomesWithError.forEach(o -> o.calculateWeight(minError, beta));
		}
		hintMap.values().forEach(Collections::sort);
	}

	public static HintSet fromFolder(String name, RatingConfig config, String path,
			String... problems) throws IOException {
		HintSet set = new HintSet(name, config);
		for (String assignment : problems) {
			File rootFolder = new File(path, assignment);
			if (!rootFolder.exists()) {
				throw new IOException("Missing hint directory: " + rootFolder);
			}
			for (File file : rootFolder.listFiles()) {
				HintOutcome edit = HintOutcome.parse(file);
				set.add(edit);
			}
		}
		set.finish();
		return set;
	}
}