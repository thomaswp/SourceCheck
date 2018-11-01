package edu.isnap.unittest;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import edu.isnap.ctd.hint.HintGenerator;
import edu.isnap.dataset.Assignment;
import edu.isnap.hint.HintMapBuilder;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.parser.Store;

public class TestRunner {

	PrintStream out = System.out;
	PrintStream err = System.out;
	long start = System.currentTimeMillis();
	List<String> unloaded = new ArrayList<>();
	List<String> unexpectedSuccesses = new ArrayList<>();
	HashMap<UnitTest, HintGenerator> failedTests = new LinkedHashMap<>();
	int passed = 0, expectedFailures = 0;

	private void addRun(Assignment assignment) {
		File testDir = new File(assignment.unitTestDir());
		if (!testDir.exists()) return;
		out.println("Testing assignment: " + assignment.name);

		HintMapBuilder builder = new SnapHintBuilder(assignment)
				.buildGenerator(Store.Mode.Ignore, 1);
		HintGenerator generator = builder.hintGenerator();

		for (File folder : new File(assignment.unitTestDir()).listFiles()) {
			String testID = folder.getName();
			String loadName = assignment.name + "/" + testID;
			out.println(loadName + " started...");
			String xml, hint;
			try {
				xml = new String(Files.readAllBytes(
						new File(folder, "code.xml").toPath()));
			} catch (Exception e) {
				e.printStackTrace();
				err.println("Failed to load code.xml for " + loadName);
				unloaded.add(loadName);
				continue;
			}
			try {
				hint = new String(Files.readAllBytes(
						new File(folder, "hint.json").toPath()));
			} catch (Exception e) {
				e.printStackTrace();
				err.println("Failed to load hint.json for " + loadName);
				unloaded.add(loadName);
				continue;
			}

			UnitTest test = new UnitTest(loadName, assignment, xml, hint);
			boolean success = test.run(generator, err);
			if (!success) {
				// Get the hints again to allow for debugging
				test.getHints(generator, out);
			}
			if (test.expectedFailure()) {
				if (success) {
					out.println(loadName + " passed!");
					unexpectedSuccesses.add(loadName);
				} else {
					err.println(loadName + " failed!");
					expectedFailures++;
				}
			} else {
				if (success) {
					out.println(loadName + " passed!");
					passed++;
				} else {
					failedTests.put(test, generator);
					err.println(loadName + " failed!");
				}
			}

		}
		out.println();
	}

	private void finish() {
		if (unloaded.size() > 0) {
			out.println("Missing tests:");
			for (String test : unloaded) out.println("\t" + test);
		}

		if (failedTests.size() > 0) {
			out.println("Failed tests:");
			for (UnitTest test : failedTests.keySet()) {
				out.println(test.id + ":");
				test.run(failedTests.get(test), err);
				out.println();
			}
		}

		long time = System.currentTimeMillis() - start;
		out.printf(
				"All tests finished in %.02f seconds: %d passed, %d missing, " +
						"%d expected failures, %d failed\n",
				time / 1000f, passed, unloaded.size(), expectedFailures,
				failedTests.size());

		if (unexpectedSuccesses.size() > 0) {
			out.println("\nUnexpected successes:");
			for (String success : unexpectedSuccesses) out.println("\t" + success);
		}
	}

	public static void run(Assignment[] assignments) {
		TestRunner runner = new TestRunner();
		for (Assignment assignment : assignments) {
			runner.addRun(assignment);
		}
		runner.finish();
	}

	public static void run(Assignment assignment) {
		TestRunner runner = new TestRunner();
		runner.addRun(assignment);
		runner.finish();
	}
}
