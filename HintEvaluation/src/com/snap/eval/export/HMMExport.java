package com.snap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;

import distance.RTED_InfoTree_Opt;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.parser.Store.Mode;
import util.LblTree;

public class HMMExport {

	private final static String[] HEADER = {
		"id", "time", "message", "data", "distance"
	};

	public static void main(String[] args) throws Exception {
		for (Assignment assignment : Fall2016.All) {
			System.out.println("Generating logs for: " + assignment);
			export(assignment);
		}
//		export(Assignment.Fall2015.GuessingGame3);
	}

	public static void export(Assignment assignment)
			throws FileNotFoundException, IOException, InterruptedException {
		final File folder = new File(assignment.dir("analysis/hmm"));
		folder.mkdirs();

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		final AtomicInteger threadCount = new AtomicInteger(0);
		for (final String attemptID : attempts.keySet()) {
			final AssignmentAttempt attempt = attempts.get(attemptID);
			if (attempt.submittedSnapshot == null) continue;
			final File file = new File(folder, attemptID + ".csv");
			threadCount.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						createHMMLog(file, attempt);
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("Completed: " + attemptID);
					threadCount.decrementAndGet();
				}
			}).start();
		}
		while (threadCount.get() > 0) {
			Thread.sleep(100);
		}
	}

	private static void createHMMLog(File file, AssignmentAttempt attempt)
			throws FileNotFoundException, IOException {
		LblTree submitted = SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true).toTree();

		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
		CSVPrinter printer = new CSVPrinter(new PrintStream(file),
				CSVFormat.DEFAULT.withHeader(HEADER));

		List<Object[]> deferred = new LinkedList<>();
		int lastDistance = -1;
		for (AttemptAction action : attempt) {
			if (action.snapshot != null) {
				Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
				lastDistance = (int) Math.round(
						opt.nonNormalizedTreeDist(node.toTree(), submitted));
				while (!deferred.isEmpty()) {
					Object[] dRow = deferred.remove(0);
					dRow[dRow.length - 1] = lastDistance;
					printer.printRecord(dRow);
				}
			}
			Object[] row = new Object[] {
				action.id, action.timestamp.getTime() / 1000, action.message, action.data,
				lastDistance
			};
			if (lastDistance == -1) {
				deferred.add(row);
				continue;
			}
			printer.printRecord(row);
		}
		printer.close();
		if (lastDistance == -1) System.err.println("No snapshots: " + file.getPath());
	}

}
