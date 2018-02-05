package edu.isnap.eval.tutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.CSC200Solutions;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.python.PythonHintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.ColdStart;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RateHints;
import edu.isnap.rating.RateHints.HintRatingSet;
import edu.isnap.rating.RatingConfig;
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

		RatingDataset dataset = iSnap2017;
		Source source = Source.StudentData;
		boolean debug = false;
		boolean writeHints = false;

		// Exporting things
//		dataset.exportTrainingData();
//		dataset.writeGoldStandard();
//		dataset.writeHintSet(source);

//		dataset.verifyGoldStandard();

		dataset.runHintRating(source, debug, writeHints);

//		dataset.writeColdStart(200, 1);

		// Tutor consensus hint generation
//		compareHintsSnap(Fall2016.instance);
//		compareHintsSnap(Spring2017.instance);
//		compareHintsPython("../data/itap");

		// Test with Fall 2017 preliminary tutor hints
//		testFall2017Pelim();
	}

	public static RatingDataset iSnap2017 = new RatingDataset() {

		@Override
		GoldStandard generateGoldStandard() throws FileNotFoundException, IOException {
			GoldStandard fall2016Standard = readConsensusSnap(Fall2016.instance, CONSENSUS_GG_SQ);
			GoldStandard spring2017Standard =
					readConsensusSnap(Spring2017.instance, CONSENSUS_GG_SQ);
			GoldStandard standard = GoldStandard.merge(fall2016Standard, spring2017Standard);
			return standard;
		}

		@Override
		HintConfig getHintConfig() {
			return new SnapHintConfig();
		}

		@Override
		String getDataDir() {
			return RateHints.ISNAP_DATA_DIR;
		}

		@Override
		String getTemplateDir(Source source) {
			String dir = source == Source.Template ? "" : "/single";
			return CSC200Solutions.dataDir + dir;
		}

		@Override
		void exportTrainingData() throws FileNotFoundException, IOException {
			exportRatingDatasetSnap(Spring2017.instance, CONSENSUS_GG_SQ,
					"hint-eval", Spring2017.Squiral, Spring2017.GuessingGame1);
			exportRatingDatasetSnap(Fall2016.instance, CONSENSUS_GG_SQ,
					"hint-eval", Fall2016.Squiral, Fall2016.GuessingGame1);
		}
	};

	public static RatingDataset ITAP2016 = new RatingDataset() {


		@Override
		GoldStandard generateGoldStandard() throws FileNotFoundException, IOException {
			GoldStandard standard = readConsensusPython("../data/itap");
			return standard;
		}

		@Override
		HintConfig getHintConfig() {
			return new PythonHintConfig();
		}

		@Override
		String getDataDir() {
			return RateHints.ITAP_DATA_DIR;
		}

		@Override
		String getTemplateDir(Source source) {
			String dir = source == Source.Template ? "" : "single/";
			return "../data/itap/" + dir + "templates";
		}

		@Override
		void exportTrainingData() throws IOException {
			exportRatingDatasetPython("../../PythonAST/data", "../data/itap",
					RateHints.ITAP_DATA_DIR);
		}
	};

	public static abstract class RatingDataset {

		abstract GoldStandard generateGoldStandard() throws FileNotFoundException, IOException;
		abstract HintConfig getHintConfig();
		abstract String getDataDir();
		abstract String getTemplateDir(Source source);
		abstract void exportTrainingData() throws IOException;

		public GoldStandard readGoldStandard() throws FileNotFoundException, IOException {
			return GoldStandard.parseSpreadsheet(getDataDir() + RateHints.GS_SPREADSHEET);
		}

		public HighlightHintSet getHintSet(Source source, GoldStandard standard)
				throws IOException {
			HighlightHintSet hintSet;
			if (source == Source.StudentData) {
				hintSet = new ImportHighlightHintSet("sourcecheck", getHintConfig(),
						 getDataDir() + RateHints.TRAINING_DIR);
			} else {
				hintSet = new TemplateHighlightHintSet("template", getTemplateDir(source),
						getHintConfig());
			}
			hintSet.addHints(standard);
			return hintSet;
		}

		public void runHintRating(Source source, boolean debug, boolean write)
				throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			HighlightHintSet hintSet = getHintSet(source, standard);
			HintRatingSet rate = RateHints.rate(standard, hintSet, debug);
			if (write) {
				String name = getSourceName(source) + ".csv";
				rate.writeAllHints(getDataDir() + RateHints.ALGORITHMS_DIR + "/" + name);
				Spreadsheet spreadsheet = new Spreadsheet();
				rate.writeAllRatings(spreadsheet);
				spreadsheet.write(getDataDir() + "analysis/ratings-" + name);
			}
		}

		public static String getSourceName(Source source) {
			String name = "sourcecheck";
			if (source == Source.Template) name += "-template";
			if (source == Source.SingleExpert) name += "-expert1";
			return name;
		}

		public void writeHintSet(Source source) throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			HighlightHintSet hintSet = getHintSet(source, standard);
			String name = getSourceName(source);
			hintSet.writeToFolder(new File(getDataDir(),
					RateHints.ALGORITHMS_DIR + getSourceName(source)).getPath(), true);
		}

		public void writeGoldStandard() throws FileNotFoundException, IOException {
			generateGoldStandard().writeSpreadsheet(getDataDir() + RateHints.GS_SPREADSHEET);
		}

		public void writeColdStart(int rounds, int step) throws IOException {
			ColdStart coldStart = getColdStart();
			coldStart.writeTest(getDataDir() + "analysis/cold-start.csv", rounds, step);
		}

		public void writeSingleTraces() throws FileNotFoundException, IOException {
			ColdStart coldStart = getColdStart();
			coldStart.testSingleTraces().write(getDataDir() + "analysis/traces.csv");
		}

		public void writeCostsSpreadsheet() throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			TrainingDataset dataset = getTrainingDataset();
			HighlightHintGenerator.getCostsSpreadsheet(dataset, standard, getHintConfig())
			.write(getDataDir() + "analysis/distances.csv");
		}
		private TrainingDataset getTrainingDataset() throws IOException {
			return TrainingDataset.fromDirectory("",
					getDataDir() + RateHints.TRAINING_DIR);
		}

		private ColdStart getColdStart() throws FileNotFoundException, IOException {
			GoldStandard standard = readGoldStandard();
			TrainingDataset dataset = getTrainingDataset();
			HighlightHintGenerator hintGenerator = new HighlightHintGenerator(getHintConfig());
			ColdStart coldStart = new ColdStart(standard, dataset, hintGenerator);
			return coldStart;
		}

		private void verifyGoldStandard() throws FileNotFoundException, IOException {
			GoldStandard gs1 = readGoldStandard();
			GoldStandard gs2 = generateGoldStandard();

			List<String> rqs1 =
					gs1.getHintRequests().stream().map(r -> r.id).collect(Collectors.toList());
			List<String> rqs2 =
					gs2.getHintRequests().stream().map(r -> r.id).collect(Collectors.toList());

			if (!rqs1.equals(rqs2)) {
				System.out.println("Read: " + rqs1);
				System.out.println("Gen:  " + rqs2);
			}

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
		fall2017.remove("guess1Lab");
		GoldStandard standard = new GoldStandard(fall2017);
		HighlightHintSet hintSet = new TemplateHighlightHintSet(
				"template", CSC200Solutions.instance);
		hintSet.addHints(standard);
		RateHints.rate(standard, hintSet, false);
		runConsensus(Fall2016.instance, standard)
		.writeAllHints(Fall2017.GuessingGame1.exportDir() + "/fall2016-rating.csv");
		runConsensus(Spring2017.instance, standard);
	}

	protected static void writeHighlight(String dataDirectory, String name, GoldStandard standard,
			HintConfig hintConfig)
			throws FileNotFoundException, IOException {
		String trainingDirectory = new File(dataDirectory, RateHints.TRAINING_DIR).getPath();
		HighlightHintSet hintSet = new ImportHighlightHintSet(name, hintConfig, trainingDirectory);
		hintSet.addHints(standard.getHintRequests());
		hintSet.writeToFolder(new File(
				dataDirectory, RateHints.ALGORITHMS_DIR + File.separator + name).getPath(), true);
	}

	protected static HintRatingSet runConsensus(Dataset trainingDataset, GoldStandard standard)
			throws FileNotFoundException, IOException {
		HighlightHintSet hintSet = new DatasetHighlightHintSet(
			trainingDataset.getName(), new SnapHintConfig(), trainingDataset)
				.addHints(standard.getHintRequests());
		return RateHints.rate(standard, hintSet);
//		hintSet.toTutorEdits().forEach(e -> System.out.println(
//				e.toSQLInsert("handmade_hints", "highlight", 20000, false, true)));
	}
}
