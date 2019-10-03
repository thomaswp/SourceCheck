package edu.isnap.eval.milestones;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import distance.RTED_InfoTree_Opt;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.datasets.csc200.Fall2016;
import edu.isnap.eval.util.PrintUpdater;
import edu.isnap.feature.CodeShapeRule;
import edu.isnap.feature.DisjunctionRule;
import edu.isnap.feature.Feature;
import edu.isnap.feature.PQGram;
import edu.isnap.feature.PQGramRule;
import edu.isnap.hint.util.Alignment;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.ListMap;
import util.LblTree;

@SuppressWarnings("unused")
public class FeatureExtraction {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		writeFeatures(true);
//		readFeatures();
//		writeDistance();
//		readDistance();
//		testRPairs();
//		extractNodeAndEdges();
//		exportFeatures(Fall2016.Squiral);
	}

	private static Assignment out = CSC200.GuessingGame1;
	private static Assignment[] trainingData = new Assignment[] {
			// Use not-Fall2016 data so training and test are separate (duh :P)
//			Spring2016.Squiral, Spring2017.Squiral,
			// But also test with same training/test data for training accuracy
//			Fall2016.Squiral,
//			Spring2016.Squiral, Spring2017.Squiral, Fall2016.Squiral,

//			Fall2016.GuessingGame1
			CSC200.GuessingGame1
	};
	private static Assignment testData = CSC200.GuessingGame1;

	private static Map<AssignmentAttempt, List<Node>> loadTrainingData() {
		Map<AssignmentAttempt, List<Node>> data = loadAssignments(trainingData);
		System.out.println("Raw training data size: " + data.size());
		return data;
	}

	private static String dataName() {
		return Arrays.stream(trainingData)
					.map(assignment -> assignment.dataset.getName())
					.map(name -> name.substring(0, 1) +
							name.substring(name.length() - 2, name.length()))
					.collect(Collectors.joining());
	}

	private static void extractNodeAndEdges() throws FileNotFoundException, IOException {

		Spreadsheet spreadsheet = new Spreadsheet();
		List<AssignmentAttempt> attempts = SelectProjects.selectAttempts(testData);

		int nActions = attempts.stream().mapToInt(attempt -> attempt.size()).sum();
		PrintUpdater updater = new PrintUpdater(50, nActions);
		for (AssignmentAttempt attempt : attempts) {
			for (AttemptAction action : attempt) {
				updater.incrementValue();
				if (action.snapshot == null) continue;
				spreadsheet.newRow();
				spreadsheet.put("traceID", attempt.id);
				spreadsheet.put("RowID", action.id);
				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
				spreadsheet.put("codestate", node.toString().hashCode());
			}
		}
		spreadsheet.write(String.format("%s/feature-codestates.csv", out.analysisDir()));
	}

	private static void testRPairs() throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(out.analysisDir() + "/rpairs.csv"),
				CSVFormat.DEFAULT.withHeader());

		HashMap<String, Node> rows = new HashMap<>();
		for (CSVRecord record : parser) {
			rows.put(record.get("rowA"), null);
			rows.put(record.get("rowB"), null);
		}
		parser.close();

		Map<String, AssignmentAttempt> load = Fall2016.Squiral.load(Mode.Use, false);
		for (AssignmentAttempt attempt : load.values()) {
			for (AttemptAction action : attempt.rows) {
				if (action.snapshot == null) continue;
				String rowID = String.valueOf(action.id);
				if (rows.containsKey(rowID)) {
					Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
					rows.put(rowID, node);
				}
			}
		}

		parser = new CSVParser(new FileReader(out.analysisDir() + "/rpairs.csv"),
				CSVFormat.DEFAULT.withHeader());
		Spreadsheet spreadsheet = new Spreadsheet();
		RTED_InfoTree_Opt rted = new RTED_InfoTree_Opt(1, 1, 1);
		for (CSVRecord record : parser) {
			String rowA = record.get("rowA");
			String rowB = record.get("rowB");
			Node nodeA = rows.get(rowA);
			Node nodeB = rows.get(rowB);

			double distance = Double.parseDouble(record.get("distance"));
			double predDisTED = rted.nonNormalizedTreeDist(nodeA.toTree(), nodeB.toTree());

			double predDisSED = Alignment.alignCost(nodeA.depthFirstIteration(),
					nodeB.depthFirstIteration());

			spreadsheet.newRow();
			spreadsheet.put("rowA", rowA);
			spreadsheet.put("rowB", rowB);
			spreadsheet.put("distance", distance);
			spreadsheet.put("predDisTED", predDisTED);
			spreadsheet.put("predDisSED", predDisSED);
		}
		parser.close();
		spreadsheet.write(out.analysisDir() + "/rpairs-dis.csv");
	}

	private static void writeDistance() throws IOException {
		Map<AssignmentAttempt, List<Node>> traceMap = loadTrainingData();

		Random rand = new Random(1234);
		List<Node> samples = new ArrayList<>();
		List<Integer> sizes = new ArrayList<>();
		for (List<Node> list : traceMap.values()) {
			sizes.add(list.size());
			// Half of 2nd quartile size
			for (int i = 0; i < 33 && !list.isEmpty(); i++) {
				samples.add(list.remove(rand.nextInt(list.size())));
			}
		}
		Collections.sort(sizes);
		System.out.println("Median size: " + sizes.get(sizes.size() / 2));
		System.out.println("2nd quarile size: " + sizes.get(sizes.size() / 4));

		int nSamples = samples.size();
		System.out.println(nSamples);

		List<LblTree> trees = samples.stream().map(Node::toTree).collect(Collectors.toList());

		double[][] distances = new double[nSamples][nSamples];
		RTED_InfoTree_Opt rted = new RTED_InfoTree_Opt(1, 1, 1);
		PrintUpdater updater = new PrintUpdater(50, nSamples * nSamples);
		for (int i = 0; i < nSamples; i++) {
			LblTree treeA = trees.get(i);
			for (int j = i + 1; j < nSamples; j++) {
				LblTree treeB = trees.get(j);

				distances[i][j] = distances[j][i] = rted.nonNormalizedTreeDist(treeA, treeB);
				updater.addValue(2);
			}
		}
		System.out.println();
		writeMatrix(distances, out.analysisDir() + "/teds.csv");

		Spreadsheet spreadsheet = new Spreadsheet();
		for (int i = 0; i < samples.size(); i++) {
			spreadsheet.newRow();
			Node node = samples.get(i);
			spreadsheet.put("id", i);
			String json = node.toASTNode().toJSON().toString();
			spreadsheet.put("node", json);
		}
		spreadsheet.write(out.analysisDir() + "/samples.csv");
	}

	private static void readDistance() throws IOException {

		CSVParser parser = new CSVParser(
				new FileReader(out.analysisDir() + "/samples-clustered.csv"),
				CSVFormat.DEFAULT.withHeader());

		Map<LblTree, Integer> clusters = new HashMap<>();
		for (CSVRecord record : parser) {
//			int id = Integer.parseInt(record.get("id"));
			int cluster = Integer.parseInt(record.get("cluster"));
			String isMedoid = record.get("medoid");
			// Use only the medoids for speed
			if (!isMedoid.equals("TRUE")) continue;
			String json = record.get("node");
			ASTNode astNode = ASTNode.parse(json);
			clusters.put(toTree(astNode), cluster);
		}

		parser.close();

		Spreadsheet spreadsheet = new Spreadsheet();
		List<AssignmentAttempt> attempts = SelectProjects.selectAttempts(testData);
		int nActions = attempts.stream().mapToInt(attempt -> attempt.size()).sum();

		RTED_InfoTree_Opt rted = new RTED_InfoTree_Opt(1, 1, 1);
		System.out.println("Testing features: ");
		PrintUpdater updater = new PrintUpdater(50, nActions);
		for (AssignmentAttempt attempt : attempts) {
			for (AttemptAction action : attempt) {
				updater.incrementValue();
				if (action.snapshot == null) continue;
				spreadsheet.newRow();
				spreadsheet.put("traceID", attempt.id);
				spreadsheet.put("RowID", action.id);
				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);

				LblTree tree = node.toTree();
				LblTree bestTree = clusters.keySet().stream().min(
						Comparator.comparing(t -> rted.nonNormalizedTreeDist(tree, t))).get();
				int cluster = clusters.get(bestTree);
				spreadsheet.put("cluster", cluster);
			}
		}
		System.out.println();
		spreadsheet.write(String.format("%s/feature-distance-%s-%02d.csv",
				out.analysisDir(), dataName(), clusters.size()));
	}

	private static LblTree toTree(ASTNode node)  {
		LblTree tree = new LblTree(node.type, 0);
		tree.setUserObject(node);
		for (ASTNode child : node.children()) tree.add(toTree(child));
		return tree;
	}

	private static void exportFeatures(Assignment out) throws IOException {
		List<Feature> features = readClusters();
		String path = out.featuresFile();
		Kryo kryo = new Kryo();
		Output output = new Output(new FileOutputStream(path));
		kryo.writeObject(output, features);
		output.close();
	}

	// TODO: Import R to Java
	private static void go(List<CodeShapeRule> rules, List<State> states) {
		rules.stream().map(rule -> new Feature(rule, rule.index)).collect(Collectors.toList());

	}


	private static List<Feature> readClusters() throws IOException {
		Kryo kryo = new Kryo();
		Input input = new Input(new FileInputStream(out.analysisDir() + "/features.cached"));
		@SuppressWarnings("unchecked")
		ArrayList<CodeShapeRule> allRules = kryo.readObject(input, ArrayList.class);
		input.close();

		allRules.sort(Comparator.comparing(rule -> rule.index));

		CSVParser parser = new CSVParser(
				new FileReader(out.analysisDir() + "/feature-clusters.csv"),
				CSVFormat.DEFAULT.withHeader());

		Map<Integer, Feature> features = new TreeMap<>();

		for (CSVRecord record : parser) {
			int id = Integer.parseInt(record.get("id"));
			int cluster = Integer.parseInt(record.get("cluster"));
			String name = record.get("name");
			CodeShapeRule rule = allRules.get(id);
			if (rule.index != id || !rule.toString().replaceAll("\\s", "")
					.equals(name.replaceAll("\\s", ""))) {
				parser.close();
				System.out.println(rule.index);
				throw new RuntimeException("Feautres out of date: " + id + " != " + rule);
			}

			Feature feature = features.get(cluster);
			if (feature == null) features.put(cluster, feature = new Feature(rule, cluster));
			else feature.addRule(rule);
		}
		parser.close();

		features.values().forEach(System.out::println);
		ArrayList<Feature> list = new ArrayList<>(features.values());
		list.sort(Comparator.comparing(f -> f.id));
		return list;
	}

	private static void readFeatures() throws IOException {
		List<Feature> features = readClusters();
		boolean disjunct = features.stream()
				.anyMatch(f -> f.rules.stream().anyMatch(r -> r instanceof DisjunctionRule));

		Spreadsheet spreadsheet = new Spreadsheet();
		List<AssignmentAttempt> attempts = SelectProjects.selectAttempts(testData);
		int nActions = attempts.stream().mapToInt(attempt -> attempt.size()).sum();

		System.out.println("Testing features: ");
		PrintUpdater updater = new PrintUpdater(50, nActions);
		for (AssignmentAttempt attempt : attempts) {
			for (AttemptAction action : attempt) {
				updater.incrementValue();
				if (action.snapshot == null) continue;
				spreadsheet.newRow();
				spreadsheet.put("traceID", attempt.id);
				spreadsheet.put("RowID", action.id);
				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
				Set<PQGram> pqGrams = PQGram.extractAllFromNode(node);
				for (Feature feature : features) {
					spreadsheet.put("F" + feature.id, feature.isSatisfied(pqGrams) ? 1 : 0);
				}
			}
		}
		System.out.println();
		spreadsheet.write(String.format("%s/feature-shapes-%s-%02d%s.csv",
				out.analysisDir(), dataName(), features.size(),
				disjunct ? "" : "-ND"));
	}

	private static void writeFeatures(boolean disjunct) throws IOException {
		Map<AssignmentAttempt, List<Node>> traceMap = loadTrainingData();

		List<List<Node>> correctTraces = traceMap.keySet().stream()
				.filter(attempt -> attempt.grade != null && attempt.grade.average() == 1)
				.map(attempt -> traceMap.get(attempt))
				.collect(Collectors.toList());

		List<Node> correctSubmissions = correctTraces.stream()
				.map(trace -> trace.get(trace.size() - 1))
				.collect(Collectors.toList());

//		correctSubmissions.forEach(node -> System.out.println(node.id + "\n" + node.prettyPrint()));

		int n = correctSubmissions.size();
		System.out.println("Correct, graded solutions: " + n);

		Map<PQGram, PQGramRule> pqRulesMap = new HashMap<>();
		for (Node node : correctSubmissions) {
			for (PQGram gram : PQGram.extractAllFromNode(node)) {
				PQGramRule rule = pqRulesMap.get(gram);
				if (rule == null) {
					pqRulesMap.put(gram, rule = new PQGramRule(gram, n));
				}
				rule.followers.add(node.id);
			}
		}


		pqRulesMap.keySet().removeIf(gram -> pqRulesMap.get(gram).followers.size() < n * 0.1);
		List<PQGramRule> pqRules = new ArrayList<>(pqRulesMap.values());
		Collections.sort(pqRules);
		System.out.println("Initial code shapes: " + pqRulesMap.size());

		List<List<Node>> allTraces = new ArrayList<>(traceMap.values());

		calculateSnapshotVectors(pqRulesMap, allTraces);
		pqRules.forEach(rule -> rule.calculateSnapshotCount());
		removeDuplicateRules(pqRules, 0.95, 0.975);
		System.out.println("Distinct code shapes: " + pqRules.size());
		pqRules.forEach(System.out::println);


		List<DisjunctionRule> decisions = new ArrayList<>();
		if (disjunct) {
			decisions.addAll(extractDecisions(pqRules));
			Collections.sort(decisions);
			removeDuplicateRules(decisions, 0.85, 0.90);
			System.out.println("Distinct decision shapes: " + decisions.size());

			// Wait until after decisions are extracted to remove low-support rules
			decisions.removeIf(rule -> rule.support() < 0.95);
		}
		pqRules.removeIf(rule -> rule.support() < 0.90);

		List<CodeShapeRule> allRules = new ArrayList<>();
		allRules.addAll(decisions);
		allRules.addAll(pqRules);
		removeDuplicateRules(allRules, 0.95, 0.975);

		for (int i = 0; i < allRules.size(); i++) allRules.get(i).index = i;

		ListMap<PQGram, CodeShapeRule> rulesMap = new ListMap<>();
		for (PQGramRule pqRule : pqRules) {
			rulesMap.add(pqRule.pqGram, pqRule);
		}
		for (DisjunctionRule decision : decisions) {
			for (PQGramRule pqRule : decision.rules) {
				rulesMap.add(pqRule.pqGram, decision);
			}
		}

		List<State> states = new ArrayList<>();
		for (List<Node> trace : correctTraces) {
			byte[] lastState = new byte[allRules.size()];
			for (Node snapshot : trace) {
				byte[] state = new byte[allRules.size()];
				for (PQGram gram : PQGram.extractAllFromNode(snapshot)) {
					List<CodeShapeRule> rules = rulesMap.get(gram);
					if (rules == null) continue;
					rules.forEach(rule -> state[rule.index] = 1);
				}
				if (!Arrays.equals(lastState, state)) {
					states.add(new State(lastState));
					lastState = state;
				}
			}
			states.add(new State(lastState));
		}

		writeStatesMatrix(states, out.analysisDir() + "/feature-states.csv");
		writeRuleSnapshotsMatrix(pqRules, out.analysisDir() + "/feature-snapshots.csv");


		double[][] jaccardMatrix = createJaccardMatrix(allRules);
		writeMatrix(jaccardMatrix, out.analysisDir() + "/feature-jaccard.csv");

		double[][] dominateMatrix = createDominateMatrix(allRules);
		writeMatrix(dominateMatrix, out.analysisDir() + "/feature-dominate.csv");

		double[][][] featureTimingMatrix = getFeatureTimingDiffMatrix(correctTraces, allRules);
		double[][] meanFeautreTimingMatrix = getMeanFeautresOrderMatrix(featureTimingMatrix);
		writeMatrix(meanFeautreTimingMatrix, out.analysisDir() + "/feature-timing.csv");

//		double[] feautresOrderSD = getFeautresOrderSD(featureOrdersMatrix, meanFeautresOrderMatrix);
//		for (int i = 0; i < allRules.size(); i++) allRules.get(i).orderSD = feautresOrderSD[i];

//		List<Integer> order = IntStream.range(0, allRules.size()).mapToObj(i -> i)
//				.sorted((i, j) -> Double.compare(dominateMatrix[i][j], dominateMatrix[j][i]))
//				.collect(Collectors.toList());

//		List<Integer> order = IntStream.range(0, allRules.size()).mapToObj(i -> i)
//				.sorted((i, j) -> Double.compare(feautresOrderSD[i], feautresOrderSD[j]))
//				.collect(Collectors.toList());

		Spreadsheet spreadsheet = new Spreadsheet();
		System.out.println("All Rules: ");
		for (int o = 0; o < allRules.size(); o++) {
			int i = o; //order.get(o);
			CodeShapeRule rule = allRules.get(i);
			System.out.printf("%02d: %s\n", rule.index, rule);
			spreadsheet.newRow();
			spreadsheet.put("name", rule.toString());
			spreadsheet.put("id", rule.index);
			spreadsheet.put("support", rule.support());
			spreadsheet.put("orderSD", rule.orderSD);
			spreadsheet.put("snapshotCount", rule.snapshotCount);
		}
		spreadsheet.write(out.analysisDir() + "/features.csv");

		Kryo kryo = new Kryo();
		Output output = new Output(new FileOutputStream(out.analysisDir() + "/features.cached"));
		kryo.writeObject(output, allRules);
		output.close();
	}

	private static void calculateSnapshotVectors(Map<PQGram, PQGramRule> pqRulesMap,
			List<List<Node>> allTraces) {
		int nSnapshots = allTraces.stream().mapToInt(list -> list.size()).sum();
		pqRulesMap.values().forEach(rule -> rule.snapshotVector = new byte[nSnapshots]);

		System.out.println("Calculating Vectors:");
		PrintUpdater updater = new PrintUpdater(50, nSnapshots);

		int snapshotIndex = 0;
		for (List<Node> trace : allTraces) {
			for (Node snapshot : trace) {
				for (PQGram gram : PQGram.extractAllFromNode(snapshot)) {
					PQGramRule rule = pqRulesMap.get(gram);
					if (rule == null) continue;
					rule.snapshotVector[snapshotIndex] = 1;
				}
				snapshotIndex++;
				updater.incrementValue();
			}
		}
		System.out.println();
		if (snapshotIndex != nSnapshots) throw new RuntimeException();
	}

	private static Map<AssignmentAttempt, List<Node>> loadAssignments(Assignment... assignments) {
		Map<AssignmentAttempt, List<Node>> map =
				new TreeMap<>(Comparator.comparing(attempt -> attempt.id));
		for (Assignment assignment : assignments) {
			Set<String> usedHints =
					assignment.load(Mode.Use, false, true, new SnapParser.SubmittedOnly()).values()
					.stream()
					// Ignore students who used hints
					.filter(attempt -> attempt.rows.rows.stream()
							.anyMatch(action -> AttemptAction.HINT_DIALOG_DESTROY.equals(
									action.message)))
					.map(attempt -> attempt.id)
					.collect(Collectors.toSet());
			Map<AssignmentAttempt, List<Node>> loaded =
					assignment.load(Mode.Use, true, true, new SnapParser.SubmittedOnly()).values()
					.stream()
					.filter(attempt -> !usedHints.contains(attempt.id))
					.collect(Collectors.toMap(
							attempt -> attempt,
							attempt -> attempt.rows.rows.stream()
							.map(action -> SimpleNodeBuilder.toTree(action.snapshot, true))
							.collect(Collectors.toList())));
			map.putAll(loaded);
		}
		return map;
	}

	private static class State {
		public final byte[] array;
		public State(byte[] array) { this.array = array; }
	}

	protected static double[][][] getFeatureTimingDiffMatrix(List<List<Node>> traces,
			List<CodeShapeRule> rules) {
		int nRules = rules.size();
		int nTraces = traces.size();

		double[][][] diffs = new double[nTraces][nRules][nRules];

		for (int i = 0; i < traces.size(); i++) {
			List<Node> trace = traces.get(i);

			Map<CodeShapeRule, Double> timings = new HashMap<>();

			// TODO: use activeTime instead
			double time = 0;
			for (Node node : trace) {
				Set<PQGram> grams = PQGram.extractAllFromNode(node);
				for (CodeShapeRule rule : rules) {
					Double timing = timings.get(rule);
					if (rule.isSatisfied(grams)) {
						if (timing == null) timings.put(rule, time);
					} else {
						// TODO: config 3
						if (timing != null && time - timing <= 3) {
							timings.put(rule, null);
						}
					}
				}
				time++;
			}

			for (int j = 0; j < nRules; j++) {
				Double tj = timings.get(rules.get(j));
				for (int k = 0; k < j; k++) {
					Double tk = timings.get(rules.get(k));

					double diff = -1;
					if (tj != null && tk != null) {
						diff = Math.abs(tj - tk);
					}
					diffs[i][j][k] = diffs[i][k][j] = diff;
				}
			}
		}

		return diffs;
	}

	protected static double[][] getMeanFeautresOrderMatrix(double[][][] orders) {
		int nRules = orders[0].length;

		double[][] mat = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			for (int j = 0; j < nRules; j++) {
				int sum = 0, count = 0;
				for (int k = 0; k < orders.length; k++) {
					double v = orders[k][i][j];
					if (v == -1) continue;
					sum += v;
					count++;
				}
				mat[i][j] = (double) sum / count;
			}
		}

		return mat;
	}

	protected static double[] getFeautresOrderSD(int[][][] orders, double[][] means) {
		int nRules = orders[0].length;

		double[] mat = new double[nRules];
		for (int i = 0; i < nRules; i++) {
			for (int j = 0; j < nRules; j++) {
				int sum = 0;
				int count = 0;
				for (int k = 0; k < orders.length; k++) {
					double v = orders[k][i][j];
					if (v == -1) continue;
					sum += Math.pow(v - means[i][j], 2);
					count++;
				}
				mat[i] += (double) sum / count;
			}
			mat[i] = Math.sqrt(mat[i] / nRules);
		}

		return mat;
	}

	private static void writeMatrix(double[][] matrix, String path)
			throws FileNotFoundException {
		File file = new File(path);
		if (file.getParentFile() != null) file.getParentFile().mkdirs();
		PrintStream printer = new PrintStream(path);
		for (double[] row : matrix) {
			printer.println(Arrays.stream(row)
					.mapToObj(v -> String.valueOf(v))
					.collect(Collectors.joining(",")));
		}
		printer.close();
	}

	private static void writeStatesMatrix(List<State> states, String path)
			throws FileNotFoundException {
		File file = new File(path);
		if (file.getParentFile() != null) file.getParentFile().mkdirs();
		PrintStream printer = new PrintStream(path);
		for (State state : states) {
			String out = Arrays.toString(state.array);
			out = out.substring(1, out.length() - 1);
			printer.println(out);
		}
		printer.close();
	}

	private static void writeRuleSnapshotsMatrix(List<PQGramRule> rules, String path)
			throws FileNotFoundException {
		File file = new File(path);
		if (file.getParentFile() != null) file.getParentFile().mkdirs();
		PrintStream printer = new PrintStream(path);
		for (PQGramRule rule : rules) {
			String out = Arrays.toString(rule.snapshotVector);
			out = out.substring(1, out.length() - 1);
			printer.println(out);
		}
		printer.close();
	}

	private static double[][] removeDuplicateRules(List<? extends CodeShapeRule> rules, double maxSupport,
			double maxJaccard) {
		int maxFollowers = rules.get(0).snapshotVector.length;
		int nRules = rules.size();

		double[][] jaccardMatrix = createJaccardMatrix(rules);
		List<CodeShapeRule> toRemove = new ArrayList<>();
		for (int i = 0; i < nRules; i++) {
			CodeShapeRule deleteCandidate = rules.get(i);

			// Remove any rule with a very high support, since these will be trivially true
			if (deleteCandidate.snapshotCount >= maxFollowers * maxSupport) {
				toRemove.add(deleteCandidate);
//				System.out.println("-- " + deleteCandidate);
//				System.out.println();
				continue;
			}

			for (int j = nRules - 1; j > i; j--) {
				CodeShapeRule supercedeCandidate = rules.get(j);
				if (jaccardMatrix[i][j] >= maxJaccard) {
//					System.out.println("- " + deleteCandidate + "\n+ " + supercedeCandidate);
//					System.out.println();
					supercedeCandidate.duplicateRules.add(deleteCandidate);
					supercedeCandidate.duplicateRules.addAll(deleteCandidate.duplicateRules);
					toRemove.add(deleteCandidate);
					break;
				}
			}
		}
		rules.removeAll(toRemove);

		return jaccardMatrix;
	}


	private static double[][] createDominateMatrix(List<CodeShapeRule> rules) {
		int maxFollowers = rules.get(0).snapshotVector.length;
		int nRules = rules.size();

		double[][] dominateMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			CodeShapeRule ruleA = rules.get(i);
			byte[] fA = ruleA.snapshotVector;
			for (int j = i + 1; j < nRules; j++) {
				CodeShapeRule ruleB = rules.get(j);
				byte[] fB = ruleB.snapshotVector;
				int intersect = 0;
				for (int k = 0; k < maxFollowers; k++) {
					if (fA[k] == 1 && fB[k] == 1) intersect++;
				}
				dominateMatrix[i][j] = (double)intersect / ruleB.snapshotCount;
				dominateMatrix[j][i] = (double)intersect / ruleA.snapshotCount;
			}
		}
		return dominateMatrix;
	}

	private static double[][] createJaccardMatrix(List<? extends CodeShapeRule> rules) {
		int nRules = rules.size();

		System.out.printf("Creating jaccard matrix %dx%d:\n", nRules, nRules);
		PrintUpdater updater = new PrintUpdater(50, nRules * (nRules - 2) / 2);

		double[][] jaccardMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			CodeShapeRule ruleA = rules.get(i);
			for (int j = i + 1; j < nRules; j++) {
				CodeShapeRule ruleB = rules.get(j);
				double value = CodeShapeRule.jaccardDistance(ruleA, ruleB);
				jaccardMatrix[i][j] = jaccardMatrix[j][i] = value;
				updater.incrementValue();
			}
		}
		System.out.println();
		return jaccardMatrix;
	}

	private static List<DisjunctionRule> extractDecisions(List<PQGramRule> sortedRules) {
		List<DisjunctionRule> decisions = new ArrayList<>();
		for (int i = sortedRules.size() - 1; i >= 0; i--) {
			PQGramRule startRule = sortedRules.get(i);
			DisjunctionRule disjunction = new DisjunctionRule(startRule);


			while (disjunction.support() < 1) {
				// TODO: config
				double bestRatio = 0.2;
				PQGramRule bestRule = null;
				for (int j = i - 1; j >= 0; j--) {
					PQGramRule candidate = sortedRules.get(j);
					int intersect = disjunction.countIntersect(candidate);
					// TODO: config
					// Ignore tiny overlap - there can always be a fluke
					if (intersect <= candidate.followCount() * 0.05) intersect = 0;
					double ratio = (double) intersect / candidate.followCount();
					if (ratio < bestRatio) {
						bestRatio = ratio;
						bestRule = candidate;
					}
					if (ratio == 0) break;
				}

				if (bestRule == null) break;
				disjunction.addRule(bestRule);
			}
			// TODO: config
			// We only want high-support decisions that have multiple choices
			if (disjunction.rules.size() > 1 && disjunction.meanJaccard() > 0) {
				decisions.add(disjunction);
			}
		}
		return decisions;
	}

}
