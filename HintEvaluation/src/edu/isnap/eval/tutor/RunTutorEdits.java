package edu.isnap.eval.tutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.CSC200Solutions;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.python.PythonHintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.ColdStart;
import edu.isnap.rating.ColdStart.HintGenerator;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RateHints;
import edu.isnap.rating.RateHints.HintRatingSet;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.HintRequestDataset;
import edu.isnap.rating.TrainingDataset;
import edu.isnap.rating.TutorHint;
import edu.isnap.rating.TutorHint.Validity;

@SuppressWarnings("unused")
public class RunTutorEdits extends TutorEdits {

	final static String CONSENSUS_GG_SQ = "consensus-gg-sq.csv";

	private enum Source {
		StudentData,
		Template,
		SingleExpert,
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {

		RatingDataset dataset = iSnapF16F17;
		Source source = Source.StudentData;
		HintAlgorithm algorithm = SourceCheck;
		boolean debug = false;
		boolean writeHints = false;

		// Exporting things (Note: this may require some copy and paste)
//		dataset.writeGoldStandard();
//		dataset.exportTrainingAndTestData();
//		dataset.writeHintSet(algorithm, source);

		dataset.verifyGoldStandard();

		dataset.runHintRating(algorithm, source, debug, writeHints);

//		dataset.testKValues(algorithm, source, debug, writeHints, 1, 20);
//		dataset.writeColdStart(algorithm, 200, 1);

		// Tutor consensus hint generation
//		compareHintsSnap(Fall2016.instance, 10000);
//		compareHintsSnap(Spring2017.instance, 10000);
//		compareHintsSnap(Fall2017.instance, 30000);
//		compareHintsPython("../data/itap", 10000);

		// Test with Fall 2017 preliminary tutor hints
//		testFall2017Pelim();
	}

	public static RatingDataset iSnapF16F17 = new SnapRatingDataset() {

		private final Dataset[] datasets = new Dataset[] {
				Fall2016.instance, Spring2017.instance, Fall2017.instance
		};

		@Override
		String getDataDir() {
			return RateHints.ISNAP_F16_F17_DATA_DIR;
		}

		@Override
		protected Dataset[] getDatasets() {
			return datasets;
		}
	};

	public static RatingDataset iSnapF16S17 = new SnapRatingDataset() {

		private final Dataset[] datasets = new Dataset[] {
				Fall2016.instance, Spring2017.instance
		};

		@Override
		String getDataDir() {
			return RateHints.ISNAP_F16_S17_DATA_DIR;
		}

		@Override
		protected Dataset[] getDatasets() {
			return datasets;
		}
	};

	private static abstract class SnapRatingDataset extends RatingDataset {
		protected abstract Dataset[] getDatasets();

		@Override
		GoldStandard generateGoldStandard() throws FileNotFoundException, IOException {
			Dataset[] datasets = getDatasets();
			GoldStandard[] standards = new GoldStandard[datasets.length];
			for (int i = 0; i < datasets.length; i++) {
				standards[i] = readConsensusSnap(datasets[i], CONSENSUS_GG_SQ);
			}
			return GoldStandard.merge(standards);
		}

		@Override
		HintConfig createHintConfig() {
			return new SnapHintConfig();
		}

		@Override
		String getTemplateDir(Source source) {
			String dir = source == Source.Template ? "" : "/single";
			return CSC200Solutions.dataDir + dir;
		}

		@Override
		void exportTrainingAndTestData() throws FileNotFoundException, IOException {
			Dataset[] datasets = getDatasets();
			for (int i = 0; i < datasets.length; i++) {
				Map<String, Assignment> assignmentMap = datasets[i].getAssignmentMap();
				Assignment guessingGame = assignmentMap.get("guess1Lab");
				Assignment squiral = assignmentMap.get("squiralHW");
				exportRatingDatasetSnap(datasets[i], CONSENSUS_GG_SQ, "hint-eval",
						guessingGame, squiral);
			}
			System.out.println();
			System.out.println("Data exported to respective assignment/export folders.");
			System.out.println("Please copy to hint-rating directory if desired");
		}
	}

	public static RatingDataset ITAPS16 = new RatingDataset() {


		@Override
		GoldStandard generateGoldStandard() throws FileNotFoundException, IOException {
			GoldStandard standard = readConsensusPython("../data/itap");
			return standard;
		}

		@Override
		HintConfig createHintConfig() {
			return new PythonHintConfig();
		}

		@Override
		String getDataDir() {
			return RateHints.ITAP_S16_DATA_DIR;
		}

		@Override
		String getTemplateDir(Source source) {
			String dir = source == Source.Template ? "" : "single/";
			return "../data/itap/" + dir + "templates";
		}

		@Override
		void exportTrainingAndTestData() throws IOException {
			exportRatingDatasetPython("../../PythonAST/data", "../data/itap",
					RateHints.ITAP_S16_DATA_DIR);
		}
	};

	public static abstract class RatingDataset {

		protected HintConfig hintConfig = createHintConfig();

		abstract GoldStandard generateGoldStandard() throws FileNotFoundException, IOException;
		abstract HintConfig createHintConfig();
		abstract String getDataDir();
		abstract String getTemplateDir(Source source);
		abstract void exportTrainingAndTestData() throws IOException;

		public GoldStandard readGoldStandard() throws FileNotFoundException, IOException {
			return GoldStandard.parseSpreadsheet(getDataDir() + RateHints.GS_SPREADSHEET);
		}

		public HintMapHintSet getHintSet(HintAlgorithm algorithm, Source source,
				GoldStandard standard) throws IOException {
			HintMapHintSet hintSet;
			if (source == Source.StudentData) {
				hintSet = algorithm.getHintSetFromTrainingDataset(
						hintConfig, getDataDir() + RateHints.TRAINING_DIR);
			} else {
				hintSet = algorithm.getHintSetFromTemplate(hintConfig, getTemplateDir(source));
			}
			hintSet.addHints(getRequestDataset());
			return hintSet;
		}

		public void runHintRating(HintAlgorithm algorithm, Source source, boolean debug,
				boolean write) throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			HintMapHintSet hintSet = getHintSet(algorithm, source, standard);
			HintRatingSet rate = RateHints.rate(standard, hintSet, debug);
			if (write) {
				String name = getSourceName(source) + ".csv";
				rate.writeAllHints(getDataDir() + RateHints.ALGORITHMS_DIR + "/" + name);
				Spreadsheet spreadsheet = new Spreadsheet();
				rate.writeAllRatings(spreadsheet);
				spreadsheet.write(getDataDir() + "analysis/ratings-" + name);
			}
		}

		public void testKValues(HintAlgorithm algorithm, Source source, boolean debug,
				boolean write, int minK, int maxK) throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			Spreadsheet spreadsheet = new Spreadsheet();
			for (int k = minK; k <= maxK; k++) {
				System.out.println("------ k = " + k + " ------");
				hintConfig.votingK = k;
				HintMapHintSet hintSet = getHintSet(algorithm, source, standard);
				HintRatingSet rate = RateHints.rate(standard, hintSet, debug);
				if (write) {
					spreadsheet.setHeader("k", k);
					rate.writeAllRatings(spreadsheet);
				}
			}
			if (write) {
				String name = getSourceName(source) + ".csv";
				spreadsheet.write(getDataDir() + "analysis/k-test-" + name);
			}
		}

		public static String getSourceName(Source source) {
			String name = "sourcecheck";
			if (source == Source.Template) name += "-template";
			if (source == Source.SingleExpert) name += "-expert1";
			return name;
		}

		public void writeHintSet(HintAlgorithm algorithm, Source source)
				throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			HintMapHintSet hintSet = getHintSet(algorithm, source, standard);
			String name = getSourceName(source);
			hintSet.writeToFolder(new File(getDataDir(),
					RateHints.ALGORITHMS_DIR + getSourceName(source)).getPath(), true);
		}

		public void writeGoldStandard() throws FileNotFoundException, IOException {
			generateGoldStandard().writeSpreadsheet(getDataDir() + RateHints.GS_SPREADSHEET);
		}

		public void writeColdStart(HintAlgorithm algorithm, int rounds, int step)
				throws IOException {
			ColdStart coldStart = getColdStart(algorithm);
			coldStart.writeTest(String.format("%sanalysis/cold-start-%03d-%d.csv",
					getDataDir(), rounds, step), rounds, step);
		}

		public void writeSingleTraces(HintAlgorithm algorithm)
				throws FileNotFoundException, IOException {
			ColdStart coldStart = getColdStart(algorithm);
			coldStart.testSingleTraces().write(getDataDir() + "analysis/traces.csv");
		}

		public void writeCostsSpreadsheet() throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			TrainingDataset dataset = getTrainingDataset();
			HighlightHintGenerator.getCostsSpreadsheet(dataset, standard, hintConfig)
			.write(getDataDir() + "analysis/distances.csv");
		}

		private TrainingDataset getTrainingDataset() throws IOException {
			return TrainingDataset.fromDirectory("",
					getDataDir() + RateHints.TRAINING_DIR);
		}

		private HintRequestDataset getRequestDataset() throws IOException {
			return HintRequestDataset.fromDirectory("",
					getDataDir() + RateHints.REQUEST_DIR);
		}

		private ColdStart getColdStart(HintAlgorithm algorithm)
				throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			TrainingDataset dataset = getTrainingDataset();
			HintRequestDataset requests = getRequestDataset();
			HintGenerator hintGenerator = algorithm.getHintGenerator(hintConfig);
			ColdStart coldStart = new ColdStart(standard, dataset, requests, hintGenerator);
			return coldStart;
		}

		private void verifyGoldStandard() throws FileNotFoundException, IOException {
			GoldStandard gs1 = readGoldStandard();
			GoldStandard gs2 = generateGoldStandard();

			for (String assignmentID : gs1.getAssignmentIDs()) {
				for (String requestID : gs1.getRequestIDs(assignmentID)) {
					List<TutorHint> edits1 = gs1.getValidHints(assignmentID, requestID);
					List<TutorHint> edits2 = gs2.getValidHints(assignmentID, requestID);

					for (int i = 0; i < edits1.size(); i++) {
						TutorHint e1 = edits1.get(i);
						TutorHint e2 = edits2.get(i);

						if (!e1.to.equals(e2.to, true, true)) {
							System.out.println("Differ: " + e1.requestID);
							System.out.println(Diff.diff(
									e1.to.prettyPrint(true, RatingConfig.Snap),
									e2.to.prettyPrint(true, RatingConfig.Snap)));
						}
					}
				}
			}
		}
	}

	private static HintAlgorithm SourceCheck = new HintAlgorithm() {

		@Override
		public HintMapHintSet getHintSetFromTrainingDataset(HintConfig config, String directory)
				throws IOException {
			return new ImportHighlightHintSet("SourceCheck", config, directory);
		}

		@Override
		public HintMapHintSet getHintSetFromTemplate(HintConfig config, String directory) {
			return new TemplateHighlightHintSet("SourceCheck", config, directory);
		}

		@Override
		public HintGenerator getHintGenerator(HintConfig config) {
			return new HighlightHintGenerator(config);
		}
	};

	private static HintAlgorithm CTD = new HintAlgorithm() {

		@Override
		public HintMapHintSet getHintSetFromTrainingDataset(HintConfig config, String directory)
				throws IOException {
			return new CTDHintSet("CTD", config, directory);
		}

		@Override
		public HintMapHintSet getHintSetFromTemplate(HintConfig config, String directory) {
			throw new UnsupportedOperationException("CTD does not fully support templates.");
		}

		@Override
		public HintGenerator getHintGenerator(HintConfig config) {
			// TODO: refactor HighlightHintGenerator to support both algorithms
			throw new UnsupportedOperationException();
		}
	};

	private static HintAlgorithm PQGram = new HintAlgorithm() {

		@Override
		public HintMapHintSet getHintSetFromTrainingDataset(HintConfig config, String directory)
				throws IOException {
			return new PQGramHintSet("PQGram", config, directory);
		}

		@Override
		public HintMapHintSet getHintSetFromTemplate(HintConfig config, String directory) {
			throw new UnsupportedOperationException("PQGram does not support templates.");
		}

		@Override
		public HintGenerator getHintGenerator(HintConfig config) {
			// TODO: refactor HighlightHintGenerator to support both algorithms
			throw new UnsupportedOperationException();
		}
	};

	private static interface HintAlgorithm {
		HintMapHintSet getHintSetFromTrainingDataset(HintConfig config, String directory)
				throws IOException;
		HintMapHintSet getHintSetFromTemplate(HintConfig config, String directory);
		HintGenerator getHintGenerator(HintConfig config);
	}

	private static void printTutorInternalRatings(Dataset dataset)
			throws FileNotFoundException, IOException {
		GoldStandard standard = readConsensusSnap(dataset, CONSENSUS_GG_SQ);
		// Compare tutor hints to each other
		Map<String, HintSet> hintSets = readTutorHintSets(dataset);
		for (HintSet hintSet : hintSets.values()) {
			System.out.println("------------ " + hintSet.name + " --------------");
			RateHints.rate(standard, hintSet);
		}
	}

	private static void testFall2017Pelim() throws FileNotFoundException, IOException {

		ListMap<String,PrintableTutorHint> fall2017 =
				readTutorEditsSnap(Fall2017.instance);
		fall2017.values().forEach(list -> list.forEach(hint -> hint.validity = Validity.OneTutor));
//		fall2017.remove("guess1Lab");
		GoldStandard standard = new GoldStandard(fall2017);
		HighlightHintSet hintSet = new TemplateHighlightHintSet(
				"template", CSC200Solutions.instance);
		hintSet.addHints(standard);
		RateHints.rate(standard, hintSet, true);
//		runConsensus(Fall2016.instance, standard)
//		.writeAllHints(Fall2017.GuessingGame1.exportDir() + "/fall2016-rating.csv");
//		runConsensus(Spring2017.instance, standard);
	}

	protected static void writeHighlight(String dataDirectory, String name, GoldStandard standard,
			HintConfig hintConfig)
			throws FileNotFoundException, IOException {
		String trainingDirectory = new File(dataDirectory, RateHints.TRAINING_DIR).getPath();
		HighlightHintSet hintSet = new ImportHighlightHintSet(name, hintConfig, trainingDirectory);
		hintSet.addHints(standard);
		hintSet.writeToFolder(new File(
				dataDirectory, RateHints.ALGORITHMS_DIR + File.separator + name).getPath(), true);
	}

	protected static HintRatingSet runConsensus(Dataset trainingDataset, GoldStandard standard)
			throws FileNotFoundException, IOException {
		HighlightHintSet hintSet = new DatasetHighlightHintSet(
			trainingDataset.getName(), new SnapHintConfig(), trainingDataset)
				.addHints(standard);
		return RateHints.rate(standard, hintSet);
//		hintSet.toTutorEdits().forEach(e -> System.out.println(
//				e.toSQLInsert("handmade_hints", "highlight", 20000, false, true)));
	}
}
