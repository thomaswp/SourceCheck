package edu.isnap.rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.isnap.rating.TrainingDataset.Trace;

public class ColdStart {

	private static final long DEFAULT_SEED = 1234;

	private final GoldStandard standard;
	private final TrainingDataset dataset;
	private final HintGenerator hintGenerator;

	public ColdStart(GoldStandard standard, TrainingDataset dataset, HintGenerator hintGenerator) {
		this.standard = standard;
		this.dataset = dataset;
		this.hintGenerator = hintGenerator;
	}

	public void test(int rounds, int step) {
		Random rand = new Random(DEFAULT_SEED);
		for (int i = 0; i < rounds; i++) {
			testRound(i, rand.nextInt(), step);
		}
	}

	private void testRound(int round, int seed, int step) {
		Random rand = new Random(seed);

		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> allTraces = new ArrayList<>(dataset.getTraces(assignmentID));
			GoldStandard assignmentStandard = standard.filterForAssignment(assignmentID);
			int n = allTraces.size();
			int count = 0;
			hintGenerator.clearTraces();
			while (!allTraces.isEmpty()) {
				for (int i = 0; i < step && !allTraces.isEmpty(); i++) {
					hintGenerator.addTrace(allTraces.remove(rand.nextInt(allTraces.size())));
					count++;
				}
				String name = String.format("Round: %02d, Count: %03d/%03d, Seed: 0x%08X",
						round, count, n, seed);
				HintSet hintSet = hintGenerator.generateHints(name,
						assignmentStandard.getHintRequests());
				System.out.println("==== " + name + " ===");
				RateHints.rate(assignmentStandard, hintSet);
			}
		}
	}

	public static interface HintGenerator {
		void clearTraces();
		void addTrace(Trace trace);
		HintSet generateHints(String name, List<HintRequest> hintRequests);
	}
}
