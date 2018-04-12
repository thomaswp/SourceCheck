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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import distance.RTED_InfoTree_Opt;
import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Alignment;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.eval.util.PrintUpdater;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import util.LblTree;

public class FeatureExtraction {

	public static void main(String[] args) throws FileNotFoundException, IOException {
//		writeFeatures(true);
//		readFeatures();
//		writeDistance();
//		readDistance();
		testRPairs();
	}

	private static Assignment out = CSC200.Squiral;
	private static Assignment[] trainingData = new Assignment[] {
			// Use not-Fall2016 data so training and test are separate (duh :P)
			Spring2016.Squiral, Spring2017.Squiral,
			// But also test with same training/test data for training accuracy
//			Fall2016.Squiral,
	};

	private static Map<AssignmentAttempt, List<Node>> loadTrainingData() {
		return loadAssignments(trainingData);
	}

	private static String dataName() {
		return Arrays.stream(trainingData)
					.map(assignment -> assignment.dataset.getName())
					.map(name -> name.substring(0, 1) +
							name.substring(name.length() - 2, name.length()))
					.collect(Collectors.joining());
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
		for (List<Node> list : traceMap.values()) {
			for (int i = 0; i < 25 && !list.isEmpty(); i++) {
				samples.add(list.remove(rand.nextInt(list.size())));
			}
		}

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
		writeMatrix(distances, CSC200.Squiral.analysisDir() + "/teds.csv");

		Spreadsheet spreadsheet = new Spreadsheet();
		for (int i = 0; i < samples.size(); i++) {
			spreadsheet.newRow();
			Node node = samples.get(i);
			spreadsheet.put("id", i);
			String json = node.toASTNode().toJSON().toString();
			spreadsheet.put("node", json);
		}
		spreadsheet.write(CSC200.Squiral.analysisDir() + "/samples.csv");
	}

	private static void readDistance() throws IOException {
		Assignment out = CSC200.Squiral;

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
		List<AssignmentAttempt> attempts = SelectSquiralProjects.selectAttempts();
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

	private static void readFeatures() throws IOException {
		Assignment out = CSC200.Squiral;

		Kryo kryo = new Kryo();
		Input input = new Input(new FileInputStream(out.analysisDir() + "/features.cached"));
		@SuppressWarnings("unchecked")
		ArrayList<Rule> allRules = kryo.readObject(input, ArrayList.class);
		input.close();

		allRules.sort(Comparator.comparing(rule -> rule.index));

		CSVParser parser = new CSVParser(
				new FileReader(out.analysisDir() + "/feature-clusters.csv"),
				CSVFormat.DEFAULT.withHeader());

		Map<Integer, Feature> features = new TreeMap<>();

		boolean disjunct = false;
		for (CSVRecord record : parser) {
			int id = Integer.parseInt(record.get("id"));
			int cluster = Integer.parseInt(record.get("cluster"));
			String name = record.get("name");
			Rule rule = allRules.get(id);
			disjunct |= rule instanceof Disjunction;
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

		Spreadsheet spreadsheet = new Spreadsheet();
		List<AssignmentAttempt> attempts = SelectSquiralProjects.selectAttempts();
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
				Set<PQGram> pqGrams = extractPQGrams(node);
				for (Feature feature : features.values()) {
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
		System.out.println(n);

		Map<PQGram, PQGramRule> pqRulesMap = new HashMap<>();
		for (Node node : correctSubmissions) {
			for (PQGram gram : extractPQGrams(node)) {
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

		List<List<Node>> allTraces = new ArrayList<>(traceMap.values());

		calculateSnapshotVectors(pqRulesMap, allTraces);
		pqRules.forEach(rule -> rule.calculateSnapshotCount());
		removeDuplicateRules(pqRules, 0.95, 0.975);
		pqRules.forEach(System.out::println);


		List<Disjunction> decisions = new ArrayList<>();
		if (disjunct) {
			decisions.addAll(extractDecisions(pqRules));
			Collections.sort(decisions);
			removeDuplicateRules(decisions, 0.85, 0.90);

			// Wait until after decisions are extracted to remove low-support rules
			decisions.removeIf(rule -> rule.support() < 0.95);
		}
		pqRules.removeIf(rule -> rule.support() < 0.90);

		List<Rule> allRules = new ArrayList<>();
		allRules.addAll(decisions);
		allRules.addAll(pqRules);
		removeDuplicateRules(allRules, 0.95, 0.975);

		for (int i = 0; i < allRules.size(); i++) allRules.get(i).index = i;

		ListMap<PQGram, Rule> rulesMap = new ListMap<>();
		for (PQGramRule pqRule : pqRules) {
			rulesMap.add(pqRule.pqGram, pqRule);
		}
		for (Disjunction decision : decisions) {
			for (PQGramRule pqRule : decision.rules) {
				rulesMap.add(pqRule.pqGram, decision);
			}
		}

		List<State> states = new ArrayList<>();
		for (List<Node> trace : correctTraces) {
			byte[] lastState = new byte[allRules.size()];
			for (Node snapshot : trace) {
				byte[] state = new byte[allRules.size()];
				for (PQGram gram : extractPQGrams(snapshot)) {
					List<Rule> rules = rulesMap.get(gram);
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

//		int[][][] featureOrdersMatrix = getFeatureOrdersMatrix(correctTraces, pqRules);
//		double[][] meanFeautresOrderMatrix = getMeanFeautresOrderMatrix(featureOrdersMatrix);
//		writeMatrix(meanFeautresOrderMatrix, out.analysisDir() + "/feature-order.csv");

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
			Rule rule = allRules.get(i);
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
				for (PQGram gram : extractPQGrams(snapshot)) {
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

	protected static int[][][] getFeatureOrdersMatrix(List<List<Node>> traces,
			List<PQGramRule> rules) {
		int nRules = rules.size();
		int nTraces = traces.size();

		Map<PQGram, Integer> ruleIndexMap = rules.stream()
				.collect(Collectors.toMap(rule -> rule.pqGram, rule -> rules.indexOf(rule)));

		int[][][] orders = new int[nTraces][nRules][nRules];

		for (int i = 0; i < traces.size(); i++) {
			List<Node> trace = traces.get(i);
			List<Integer> indices = new ArrayList<>();
			int[][] tOrders = orders[i];
			for (Node node : trace) {
				Set<PQGram> grams = extractPQGrams(node);
				for (PQGram gram : grams) {
					Integer index = ruleIndexMap.get(gram);
					if (indices.contains(index)) continue;
					if (index != null) {
						// TODO: it should be 0 if added at the same time
						int insertIndex = indices.size();
						for (int j = 0; j < indices.size(); j++) {
							int previous = indices.get(j);
							tOrders[previous][index] =
									-(tOrders[index][previous] = insertIndex - j);
						}
						indices.add(index);
					}
				}
			}
		}

		return orders;
	}

	protected static double[][] getMeanFeautresOrderMatrix(int[][][] orders) {
		int nRules = orders[0].length;

		double[][] mat = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			for (int j = 0; j < nRules; j++) {
				int sum = 0;
				for (int k = 0; k < orders.length; k++) {
					sum += orders[k][i][j];
				}
				mat[i][j] = (double) sum / orders.length;
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
				for (int k = 0; k < orders.length; k++) {
					sum += Math.pow(orders[k][i][j] - means[i][j], 2);
				}
				mat[i] += (double) sum / orders.length;
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

	private static Set<PQGram> extractPQGrams(Node node) {
		Set<PQGram> pqGrams = new HashSet<>();
		for (int p = 3; p > 0; p--) {
			for (int q = 4; q > 0; q--) {
				pqGrams.addAll(PQGram.extractFromNode(node, p, q));
			}
		}
		return pqGrams;
	}

	private static double[][] removeDuplicateRules(List<? extends Rule> rules, double maxSupport,
			double maxJaccard) {
		int maxFollowers = rules.get(0).snapshotVector.length;
		int nRules = rules.size();

		double[][] jaccardMatrix = createJaccardMatrix(rules);
		List<Rule> toRemove = new ArrayList<>();
		for (int i = 0; i < nRules; i++) {
			Rule deleteCandidate = rules.get(i);

			// Remove any rule with a very high support, since these will be trivially true
			if (deleteCandidate.snapshotCount >= maxFollowers * maxSupport) {
				toRemove.add(deleteCandidate);
//				System.out.println("-- " + deleteCandidate);
//				System.out.println();
				continue;
			}

			for (int j = nRules - 1; j > i; j--) {
				Rule supercedeCandidate = rules.get(j);
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


	private static double[][] createDominateMatrix(List<Rule> rules) {
		int maxFollowers = rules.get(0).snapshotVector.length;
		int nRules = rules.size();

		double[][] dominateMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			Rule ruleA = rules.get(i);
			byte[] fA = ruleA.snapshotVector;
			for (int j = i + 1; j < nRules; j++) {
				Rule ruleB = rules.get(j);
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

	private static double[][] createJaccardMatrix(List<? extends Rule> rules) {
		int nRules = rules.size();

		System.out.printf("Creating jaccard matrix %dx%d:\n", nRules, nRules);
		PrintUpdater updater = new PrintUpdater(50, nRules * (nRules - 2) / 2);

		double[][] jaccardMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			Rule ruleA = rules.get(i);
			for (int j = i + 1; j < nRules; j++) {
				Rule ruleB = rules.get(j);
				double value = jaccardDistance(ruleA, ruleB);
				jaccardMatrix[i][j] = jaccardMatrix[j][i] = value;
				updater.incrementValue();
			}
		}
		System.out.println();
		return jaccardMatrix;
	}

	private static double jaccardDistance(Rule ruleA, Rule ruleB) {
		byte[] fA = ruleA.snapshotVector;
		byte[] fB = ruleB.snapshotVector;
		int intersect = 0;
		for (int k = 0; k < fA.length; k++) {
			if (fA[k] == 1 && fB[k] == 1) intersect++;
		}
		double value = (double)intersect /
				(ruleA.snapshotCount + ruleB.snapshotCount - intersect);
		return value;
	}

	private static List<Disjunction> extractDecisions(List<PQGramRule> sortedRules) {
		List<Disjunction> decisions = new ArrayList<>();
		for (int i = sortedRules.size() - 1; i >= 0; i--) {
			PQGramRule startRule = sortedRules.get(i);
			Disjunction disjunction = new Disjunction(startRule);


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

	public static abstract class Rule {
		public int index;
		public double orderSD;
		public final Set<String> followers = new HashSet<>();
		public final int maxFollowers;

		protected final List<Rule> duplicateRules = new ArrayList<>();

		protected transient byte[] snapshotVector;
		protected transient int snapshotCount;

		public abstract boolean isSatisfied(Set<PQGram> pqGrams);

		public Rule(int maxFollowers) {
			this.maxFollowers = maxFollowers;
		}

		public void calculateSnapshotCount() {
			int count = 0;
			for (int j = 0; j < snapshotVector.length; j++) {
				count += snapshotVector[j];
			}
			snapshotCount = count;
		}

		public int followCount() {
			return followers.size();
		}

		public double support() {
			return (double) followers.size() / maxFollowers;
		}

		public int countIntersect(PQGramRule rule) {
			return (int) followers.stream().filter(rule.followers::contains).count();
		}

		public int countUnion(PQGramRule rule) {
			return (int) Stream.concat(followers.stream(), rule.followers.stream())
					.distinct().count();
		}

		public double jaccardDistance(PQGramRule rule) {
			int intersect = countIntersect(rule);
			return (double)intersect / (followCount() + rule.followCount() - intersect);
		}

	}

	public static class PQGramRule extends Rule implements Comparable<PQGramRule> {

		public final PQGram pqGram;

		@SuppressWarnings("unused")
		private PQGramRule() {
			this(null, 0);
		}

		public PQGramRule(PQGram pqGram, int maxFollowers) {
			super(maxFollowers);
			this.pqGram = pqGram;
		}

		@Override
		public String toString() {
			return String.format("%.02f: %s", support(), pqGram);
		}

		@Override
		public int compareTo(PQGramRule o) {
			int fc = Integer.compare(followCount(), o.followCount());
			if (fc != 0) return fc;
			return pqGram.compareTo(o.pqGram);
		}

		@Override
		public boolean isSatisfied(Set<PQGram> pqGrams) {
			return pqGrams.contains(pqGram);
		}
	}

	static class Disjunction extends Rule implements Comparable<Disjunction> {

		private List<PQGramRule> rules = new ArrayList<>();

		private Disjunction() {
			super(0);
		}

		public Disjunction(PQGramRule startRule) {
			super(startRule.maxFollowers);
			snapshotVector = new byte[startRule.snapshotVector.length];
			addRule(startRule);
		}

		private double meanJaccard() {
			int n = rules.size();
			double totalJacc = 0;
			int count = 1;
			for (int i = 0; i < n; i++) {
				PQGramRule ruleA = rules.get(i);
				for (int j = i + 1; j < n; j++) {
					totalJacc += FeatureExtraction.jaccardDistance(ruleA, rules.get(j));
					count++;
				}
			}
			return totalJacc / count;
		}

		public void addRule(PQGramRule rule) {
			rules.add(rule);
			followers.addAll(rule.followers);
			for (int i = 0; i < snapshotVector.length; i++) {
				if (rule.snapshotVector[i] == 1) snapshotVector[i] = 1;
			}
			calculateSnapshotCount();
		}

		@Override
		public String toString() {
			return String.format("%.02f:\t%s", support(), String.join(" OR\n\t\t\t\t",
					rules.stream().map(r -> (CharSequence) r.toString())::iterator));
		}

		@Override
		public int compareTo(Disjunction o) {
			int fc = Integer.compare(followCount(), o.followCount());
			if (fc != 0) return fc;

			int cr = -Integer.compare(rules.size(), o.rules.size());
			if (cr != 0) return cr;

			return rules.stream().max(PQGramRule::compareTo).get().compareTo(
					o.rules.stream().max(PQGramRule::compareTo).get());
		}

		@Override
		public boolean isSatisfied(Set<PQGram> pqGrams) {
			return rules.stream().anyMatch(rule -> rule.isSatisfied(pqGrams));
		}
	}

	static class Feature {

		private final List<Rule> rules = new ArrayList<>();
		private final int id;

		public Feature(Rule startRule, int id) {
			this.id = id;
			addRule(startRule);
		}

		public void addRule(Rule rule) {
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

	public static class PQGram implements Comparable<PQGram> {

		public final static String EMPTY = "*";

		public final String[] tokens;
		public final int p, q;
		public int count = 1;

		private final int nEmpty;
		private final int nonEmptyP, nonEmptyQ;

		private PQGram() {
			this(0, 0, new String[0]);
		}

		private PQGram(int p, int q, String[] tokens) {
			if (tokens.length != p + q) {
				throw new IllegalArgumentException("p + q must equal tokens.length");
			}

			this.p = p;
			this.q = q;
			this.tokens = tokens;

			nEmpty = (int) Arrays.stream(tokens).filter(EMPTY::equals).count();
			int pr = 0, qr = 0;
			for (int i = 0 ; i < tokens.length; i++) {
				if (EMPTY.equals(tokens[i])) continue;
				if (i < p) pr++;
				else if (i > p) qr++;
			}
			this.nonEmptyP = pr;
			this.nonEmptyQ = qr;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (obj.getClass() != getClass()) return false;
			PQGram rhs = (PQGram) obj;
			return new EqualsBuilder()
					.append(p, rhs.p)
					.append(q, rhs.q)
					.append(count, rhs.count)
					.append(tokens, rhs.tokens)
					.isEquals();
		}

		@Override
		public int hashCode() {
			// Could just cache this;
			return new HashCodeBuilder(5, 17)
					.append(p)
					.append(q)
					.append(count)
					.append(tokens)
					.toHashCode();
		}

		@Override
		public String toString() {
			String[] out = Arrays.copyOf(tokens, tokens.length);
			out[p - 1] = "{" + out[p - 1]  + "}";
			return Arrays.toString(out) + (count > 1 ? (" x" + count) : "");
		}

		private final static Comparator<PQGram> comparator =
				Comparator.comparing((PQGram gram) -> gram.nonEmptyQ)
				.thenComparing(gram -> gram.nonEmptyP)
				.thenComparing(gram -> gram.count)
				.thenComparing(gram -> -gram.nEmpty)
				.thenComparing(gram -> gram.tokens[gram.p - 1]);

		@Override
		public int compareTo(PQGram o) {
			return comparator.compare(this, o);
		}

		// Not currently in use...
		public boolean contains(PQGram o) {
			if (o.q > q) return false;
			if (o.p > p + 1) return false;
			if (o.p > p && o.q > 1) return false;

			if (o.q == 1) {
				// If the the other is a path, check if it is a subsequence of our p
				if (containsSubsequece(tokens, 0, p, o.tokens, 0, o.tokens.length)) return true;
			}

			// Next make sure that other's p is a subset of ours
			for (int i = 0; i < p && i < o.p; i++) {
				if (!StringUtils.equals(tokens[p - i - 1], o.tokens[o.p - i - 1])) return false;
			}

			// Then make sure the other's q is a subsequence of ours
			return containsSubsequece(tokens, p, q, o.tokens, o.p, o.q);
		}

		private static boolean containsSubsequece(String[] a, int startA, int lengthA,
				String[] b, int startB, int lengthB) {
			if (lengthB > lengthA) return false;

			for (int offset = 0; offset <= lengthA - lengthB; offset++) {
				boolean eq = true;
				for (int i = 0; i < lengthB; i++) {
					if (!StringUtils.equals(a[startA + i + offset], b[startB + i])) {
						eq = false;
						break;
					}
				}
				if (eq) return true;
			}
			return false;
		}

		public static String labelForNode(Node node) {
			if (node == null) return EMPTY;
			return node.type();
		}

		public static Set<PQGram> extractFromNode(Node node, int p, int q) {
			Set<PQGram> set = new HashSet<>();
			extractFromNode(node, p, q, set);
			return set;
		}

		private static void extractFromNode(Node node, int p, int q, Set<PQGram> set) {
			String[] tokens = new String[p + q];
			Arrays.fill(tokens, EMPTY);

			Node parent = node;
			for (int i = p - 1; i >= 0; i--) {
				tokens[i] = labelForNode(parent);
				if (parent != null) parent = parent.parent;
			}

			if (node.children.size() == 0) {
				set.add(new PQGram(p, q, tokens));
				return;
			}

			for (int offset = 1 - q; offset <= node.children.size() - 1; offset++) {
				tokens = Arrays.copyOf(tokens, tokens.length);
				for (int i = 0; i < q; i++) {
					int tokenIndex = i + p;
					int childIndex = i + offset;
					Node child = childIndex >= 0 && childIndex < node.children.size() ?
							node.children.get(childIndex) : null;
					tokens[tokenIndex] = labelForNode(child);
				}
				PQGram pqGram = new PQGram(p, q, tokens);
				while (!set.add(pqGram)) pqGram.count++;
			}

			for (Node child : node.children) {
				extractFromNode(child, p, q, set);
			}
		}
	}

}
