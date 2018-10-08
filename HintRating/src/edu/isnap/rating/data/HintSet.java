package edu.isnap.rating.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONObject;

import edu.isnap.node.ASTNode;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.data.HintOutcome.HintWithError;
import edu.isnap.util.map.ListMap;

public class HintSet {

	public final String name;
	public final RatingConfig config;

	private final ListMap<String, HintOutcome> hintMap = new ListMap<>();

	public HintSet(String name, RatingConfig config) {
		this.name = name;
		this.config = config;
	}

	public void add(HintOutcome outcome) {
		hintMap.add(outcome.requestID, outcome);
	}

	public Set<String> getHintRequestIDs() {
		return hintMap.keySet();
	}

	public List<HintOutcome> getOutcomes(String snapshotID) {
		List<HintOutcome> list = hintMap.get(snapshotID);
		if (list == null) list = Collections.emptyList();
		return Collections.unmodifiableList(list);
	}

	public void finish() {
		for (String id : hintMap.keySet()) {
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

	public void writeToFolder(String path, boolean clean) throws IOException {
		Set<String> madeDirs = new HashSet<>();
		for (String requestID : hintMap.keySet()) {
			List<HintOutcome> hints = hintMap.get(requestID);
			for (int i = 0; i < hints.size(); i++) {
				HintOutcome hint = hints.get(i);
				File parentDir = new File(path, hint.assignmentID);
				if (madeDirs.add(hint.assignmentID)) {
					parentDir.mkdirs();
					if (clean) {
						for (File file : parentDir.listFiles()) file.delete();
					}
				}
				String filename = String.format("%s_%02d.json", hint.requestID, i);
				File file = new File(parentDir, filename);
				JSONObject json = hint.result.toJSON();
				json.put("weight", hint.weight());
				Files.write(file.toPath(), json.toString(4).getBytes());
			}
		}
	}

	public static HintSet fromFolder(String name, RatingConfig config, String path)
			throws IOException {
		HintSet set = new HintSet(name, config);
		File rootFolder = new File(path);
		if (!rootFolder.exists()) {
			throw new IOException("Missing hint directory: " + rootFolder);
		}
		for (File assignmentDir : rootFolder.listFiles(file -> file.isDirectory())) {
			String assignmentID = assignmentDir.getName();
			for (File file : assignmentDir.listFiles()) {
				HintOutcome edit = HintOutcome.parse(file, assignmentID);
				set.add(edit);
			}
		}
		set.finish();
		return set;
	}

	public void printHints(GoldStandard standard) {
		for (String assignmentID : standard.getAssignmentIDs()) {
			System.out.println("+++++++++++++  " + assignmentID + "  +++++++++++++");
			for (String requestID : standard.getRequestIDs(assignmentID)) {
				System.out.println("RequestID: " + requestID);
				List<HintOutcome> hints = hintMap.get(requestID);
				if (hints == null || hints.isEmpty()) continue;
				List<TutorHint> validHints = standard.getValidHints(assignmentID, requestID);
				if (validHints.isEmpty()) continue;
				ASTNode from = validHints.get(0).from;
				System.out.println(from.prettyPrint(true, config));
				for (HintOutcome outcome : hints) {
					ASTNode result = outcome.result;

					System.out.println("Weight: " + outcome.weight());
					System.out.println(ASTNode.diff(from, result, config, 2));
					System.out.println("-----------------");
				}
				System.out.println("\n");
			}
		}
	}
}