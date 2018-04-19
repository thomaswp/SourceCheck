package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.RateHints.HintRatingSet;

public class ColdStart {

	private static final long DEFAULT_SEED = 1234;

	private final GoldStandard standard;
	private final HintRequestDataset requestDataset;
	private final TrainingDataset trainingDataset;
	private final HintGenerator hintGenerator;

	public ColdStart(GoldStandard standard, TrainingDataset dataset, HintRequestDataset requests,
			HintGenerator hintGenerator) {
		this.standard = standard;
		this.trainingDataset = dataset;
		this.requestDataset = requests;
		this.hintGenerator = hintGenerator;
	}

	public Spreadsheet test(int rounds, int step) {
		Spreadsheet spreadsheet = new Spreadsheet();
		runTest(rounds, step, spreadsheet);
		return spreadsheet;
	}

	private void runTest(int rounds, int step, Spreadsheet spreadsheet) {
		Random rand = new Random(DEFAULT_SEED);
		for (int i = 0; i < rounds; i++) {
			spreadsheet.setHeader("round", i);
			testRound(spreadsheet, i, rand.nextInt(), step);
		}
	}

	public void writeTest(String path, int rounds, int step)
			throws FileNotFoundException, IOException {
		Spreadsheet spreadsheet = new Spreadsheet();
		spreadsheet.beginWrite(path);
		runTest(rounds, step, spreadsheet);
		spreadsheet.endWrite();
	}

	private void testRound(Spreadsheet spreadsheet, int round, int seed, int step) {
		Random rand = new Random(seed);

		for (String assignmentID : trainingDataset.getAssignmentIDs()) {
			List<Trace> allTraces = new ArrayList<>(trainingDataset.getTraces(assignmentID));
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
				HintSet hintSet = hintGenerator.generateHints(name, requestDataset.getAllRequests());
				System.out.println("==== " + name + " ===");
				HintRatingSet ratings = RateHints.rate(assignmentStandard, hintSet);

				spreadsheet.setHeader("count", count);
				spreadsheet.setHeader("total", n);
				ratings.writeAllRatings(spreadsheet);
			}
		}
	}

	public Spreadsheet testSingleTraces() {
		Spreadsheet spreadsheet = new Spreadsheet();
		for (String assignmentID : trainingDataset.getAssignmentIDs()) {
			List<Trace> allTraces = new ArrayList<>(trainingDataset.getTraces(assignmentID));
			GoldStandard assignmentStandard = standard.filterForAssignment(assignmentID);
			for (Trace trace : allTraces) {
				hintGenerator.clearTraces();
				hintGenerator.addTrace(trace);
				HintSet hintSet = hintGenerator.generateHints(trace.id,
						requestDataset.getAllRequests());
				System.out.println("==== " + trace.id + " ===");
				HintRatingSet ratings = RateHints.rate(assignmentStandard, hintSet);

				spreadsheet.setHeader("traceID", trace.id);
				ratings.writeAllRatings(spreadsheet);
			}
		}
		return spreadsheet;
	}

	public static interface HintGenerator {
		void clearTraces();
		void addTrace(Trace trace);
		HintSet generateHints(String name, List<HintRequest> hintRequests);
	}
}
