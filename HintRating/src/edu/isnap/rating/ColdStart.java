package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.RateHints.HintRatingSet;
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

	public Spreadsheet test(int rounds, int step) {
		Random rand = new Random(DEFAULT_SEED);
		Spreadsheet spreadsheet = new Spreadsheet();
		for (int i = 0; i < rounds; i++) {
			spreadsheet.setHeader("round", i);
			testRound(spreadsheet, i, rand.nextInt(), step);
		}
		return spreadsheet;
	}

	public void writeTest(String path, int rounds, int step)
			throws FileNotFoundException, IOException {
		test(rounds, step).write(path);
	}

	private void testRound(Spreadsheet spreadsheet, int round, int seed, int step) {
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
				HintRatingSet ratings = RateHints.rate(assignmentStandard, hintSet);

				spreadsheet.setHeader("count", count);
				spreadsheet.setHeader("total", n);
				ratings.writeAllRatings(spreadsheet);
			}
		}
	}

	public static interface HintGenerator {
		void clearTraces();
		void addTrace(Trace trace);
		HintSet generateHints(String name, List<HintRequest> hintRequests);
	}
}
