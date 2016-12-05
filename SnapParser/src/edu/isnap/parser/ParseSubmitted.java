package edu.isnap.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class ParseSubmitted {

	private final static int MIN_LOG_LENGTH = 30;

	public static void printToGrade(Assignment assignment) throws IOException {
		Map<String, Submission> submittedRows = getOrParseSubmissions(assignment);
		if (submittedRows == null) throw new RuntimeException("No submitted assignments.");

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, true);
		System.out.println("\n\n");
		for (String attemptID : attempts.keySet()) {
			if (submittedRows.containsKey(attemptID)) {
				System.out.printf("%s,%s\n", attemptID,
						submittedRows.get(attemptID));
				submittedRows.remove(attemptID);
			}
		}
		// TODO: Print alternate assignment separately
		System.out.println("------------------,");
		for (String attemptID : submittedRows.keySet()) {
			System.out.println(attemptID + ",");
		}

	}

	private static HashMap<Assignment, Map<String, AssignmentAttempt>> cachedMaps =
			new HashMap<>();

	private static AssignmentAttempt getCachedAttempt(String guid, Assignment assignment) {
		if (!cachedMaps.containsKey(assignment)) {
			cachedMaps.put(assignment, assignment.load(Mode.Use, false, false));
		}
		return cachedMaps.get(assignment).get(guid);
	}

	public static void parseSubmitted(Assignment assignment) throws IOException {
		System.out.println("Parsing submitted for: " + assignment.name);
		cachedMaps.clear();
		File dir = new File(assignment.submittedDir());
		if (!dir.exists()) {
			System.err.println("Missing submitted directory: " + dir.getAbsolutePath());
			return;
		}
		String outPath = assignment.submittedDir() + ".txt";

		TreeMap<String, String> output = new TreeMap<>();

		Map<String, String> submitted = new HashMap<>();
		for (File file : dir.listFiles()) {
			if (!file.getName().toLowerCase().endsWith(".xml")) {
				System.err.println("Unknown file in submitted folder: " + file.getAbsolutePath());
				continue;
			}
			try {
				Snapshot snapshot = Snapshot.parse(file);
				if (snapshot == null) {
					System.err.println("Failed to parse: " + file.getPath());
					continue;
				}
				String guid = snapshot.guid;
				boolean noGUID = false;
				if (guid == null || guid.length() == 0) {
					guid = file.getName();
					guid = guid.substring(0, guid.length() - 4).replaceAll("nolog-", "");
					guid = "nolog-" + guid;
					noGUID = true;
				}

				String previousFile = submitted.put(guid, file.getName());
				if (previousFile != null) {
					throw new RuntimeException(String.format(
							"Duplicate submission (%s): (%s vs %s)\n",
							guid, file.getName(), previousFile));
				}

				String goodName = guid + ".xml";
				if (!file.getName().equals(goodName)) {
					boolean success = file.renameTo(new File(file.getParent(), guid + ".xml"));
					if (!success) {
						System.err.println("Failed to rename: " + file.getPath());
					}
				}

				String submittedCode = snapshot.toCode();

				// Get all the assignments to check for this submission
				List<Assignment> possibleAssignments = new LinkedList<>();
				// Don't do this for ignored assignments (but we still want to write a row for
				// completion and grading purposes)
				if (!assignment.ignore(guid)) {
					Assignment locationAssignment = assignment.getLocationAssignment(guid);
					possibleAssignments.add(locationAssignment);
					// Only add other assignments if this submission's location isn't hard-coded
					if (locationAssignment == assignment) {
						possibleAssignments.add(assignment.prequel);
						possibleAssignments.add(assignment.None);
					}
				}

				Integer overrideRowID = assignment.getSubmittedRow(guid);

				Integer rowID = null;
				String location = "";
				for (Assignment toCheck : possibleAssignments) {
					// Look for a valid submission for this assignment
					if (toCheck == null) continue;
					AssignmentAttempt attempt = getCachedAttempt(guid, toCheck);
					if (attempt == null || attempt.size() < MIN_LOG_LENGTH) continue;

					// If this is the first valid assignment, we set the location
					if (location.isEmpty()) location = toCheck.name;

					// We then check for a matching row
					rowID = findMatchingRow(attempt, guid, submittedCode, overrideRowID);
					if (rowID != null) {
						location = toCheck.name;
						break;
					}
				}
				output.put(guid, String.format("%s,%s,%d",
						guid, location, rowID == null ? -1 : rowID));

				// Check for undesirable outcomes
				if (!location.isEmpty()) {
					if (rowID == null) {
						// We at least one found a valid attempt, but no matching row
						System.out.printf("Submitted code not found for: %s/%s (%s)\n",
								location, guid, file.getPath());
					}
				} else if (!noGUID && !assignment.ignore(guid)) {
					// We found no matching attempt, but there was a GUID in the submission
					System.err.printf("No logs found for: %s/%s (%s)\n",
							location, guid, file.getPath());
				}

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

		PrintWriter writer = new PrintWriter(outPath);
		for (String out : output.values()) {
			writer.println(out);
		}
		writer.close();
	}

	private static Integer findMatchingRow(AssignmentAttempt attempt, String guid,
			String submittedCode, Integer overrideRowID) {
		Integer rowID = null;
		String lastCode = null;
		for (AttemptAction action : attempt) {
			if (action.snapshot != null) {
				lastCode = action.snapshot.toCode();
			}

			if (overrideRowID == null) {
				// If the we aren't given a row to find, match code
				if (submittedCode.equals(lastCode)) {
					rowID = action.id;
					// And preference export rows
					if (AttemptAction.IDE_EXPORT_PROJECT.equals(action.message)) {
						break;
					}
				}
			} else {
				// Otherwise, only match to that row
				if (overrideRowID == action.id) {
					rowID = action.id;
					break;
				}
			}
		}
		return rowID;
	}

	public static Map<String, Submission> getOrParseSubmissions(Assignment assignment)
			throws IOException {
		Map<String, Submission> submittedRows = getSubmissions(assignment);
		if (submittedRows != null) return submittedRows;
		parseSubmitted(assignment);
		return getSubmissions(assignment);
	}

	public static Map<String, Map<String, Submission>> getAllSubmissions(Dataset dataset) {
		Map<String, Map<String, Submission>> allSubmissions = new HashMap<>();
		for (Assignment assignment : dataset.all()) {
			allSubmissions.put(assignment.name, getSubmissions(assignment));
		}
		return allSubmissions;
	}

	public static Map<String, Submission> getSubmissions(Assignment assignment) {
		Map<String, Submission> submitted = new HashMap<>();
		File file = new File(assignment.submittedDir() + ".txt");
		if (!file.exists()) return null;
		Scanner sc;
		try {
			sc = new Scanner(file);
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.length() > 0) {
					String[] parts = line.split(",");
					if (parts.length != 3) {
						sc.close();
						throw new RuntimeException("Invalid line: " + line);
					}
					String location = parts[1];
					int value = Integer.parseInt(parts[2]);
					Submission submissions = new Submission(
							location.length() == 0 ? null : location,
							value == -1 ? null : value);
					submitted.put(parts[0], submissions);
				}
			}
			sc.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		return submitted;
	}

	public static class Submission {
		public final String location;
		public final Integer submittedRowID;

		public Submission(String location, Integer submittedRowID) {
			this.location = location;
			this.submittedRowID = submittedRowID;
		}
	}
}
