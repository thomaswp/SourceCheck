package edu.isnap.eval.export;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.csc200.Fall2016;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class HMMExport {

	private final static String[] HEADER = {
		"id", "time", "category"
	};

	public static void main(String[] args) throws Exception {
//		for (Assignment assignment : Fall2016.All) {
//			System.out.println("Generating logs for: " + assignment);
//			export(assignment);
//		}
		export(Fall2016.Squiral);
	}

	private static Map<String, String> categories;

	private static Map<String, String> loadCategories() {
		if (categories != null) return categories;
		try {
			CSVParser parser = new CSVParser(new FileReader("events.csv"),
					CSVFormat.DEFAULT.withHeader());

			Map<String, String> map = new HashMap<>();

			for (CSVRecord record : parser.getRecords()) {
				String category = record.get(1);
				if (category.trim().length() == 0) category = null;
				if (record.get(2).trim().length() != 0) category = null;
				map.put(record.get(0), category);
			}

			parser.close();
			categories = map;
			return map;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

	}

	public static void export(Assignment assignment)
			throws FileNotFoundException, IOException, InterruptedException {

		CSVPrinter printer = new CSVPrinter(
				new PrintStream(assignment.analysisDir() + "/events.csv"),
				CSVFormat.DEFAULT.withHeader(HEADER));

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());

		for (final String attemptID : attempts.keySet()) {
			final AssignmentAttempt attempt = attempts.get(attemptID);
			try {
				createHMMLog(printer, attempt);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("Completed: " + attemptID);
		}

		printer.close();
	}

	private static void createHMMLog(CSVPrinter printer, AssignmentAttempt attempt)
			throws FileNotFoundException, IOException {
//		LblTree submitted = SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true).toTree();
//
//		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
		Map<String, String> categories = loadCategories();

//		List<Object[]> deferred = new LinkedList<>();
//		int lastDistance = -1;
		for (AttemptAction action : attempt) {
//			if (action.snapshot != null) {
//				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
//				lastDistance = (int) Math.round(
//						opt.nonNormalizedTreeDist(node.toTree(), submitted));
//				while (!deferred.isEmpty()) {
//					Object[] dRow = deferred.remove(0);
//					dRow[dRow.length - 1] = lastDistance;
//					printer.printRecord(dRow);
//				}
//			}
			String category = categories.get(action.message);
			if (category == null) continue;
			Object[] row = new Object[] {
				attempt.id, action.currentActiveTime, category
			};
//			if (lastDistance == -1) {
//				deferred.add(row);
//				continue;
//			}
			printer.printRecord(row);
		}
	}

}
