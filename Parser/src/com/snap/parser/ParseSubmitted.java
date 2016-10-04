package com.snap.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.snap.data.Snapshot;
import com.snap.parser.Assignment.Dataset;
import com.snap.parser.Store.Mode;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class ParseSubmitted {

	public static void main(String[] args) throws IOException {
//		for (Assignment assignment : Assignment.Fall2015.All) {
//			parse(assignment);
//		}
		parseSubmitted(Assignment.Fall2016.GuessingGame1);
//		printToGrade(Assignment.Fall2015.GuessingGame1);
	}

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

	private static void parseSubmitted(Assignment assignment) throws IOException {
		System.out.println("Parsing submitted for: " + assignment.name);
		File dir = new File(assignment.submittedDir());
		if (!dir.exists()) {
			System.err.println("Missing submitted directory: " + dir.getAbsolutePath());
			return;
		}
		String outPath = assignment.submittedDir() + ".txt";
		PrintWriter output = new PrintWriter(outPath);
		Map<String, String> submitted = new HashMap<>();
		for (File file : dir.listFiles()) {
			if (!file.getName().endsWith(".xml")) {
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
					guid = "nolog-" + guid.substring(0, guid.length() - 4);
					noGUID = true;
				}

				if (assignment.ignore(guid)) continue;

				String previousFile = submitted.put(guid, file.getName());
				if (previousFile != null) {
					throw new RuntimeException(String.format("Duplicate submission (%s): (%s vs %s)\n",
							guid, file.getName(), previousFile));
				}

				String submittedCode = snapshot.toCode();

				Assignment effectiveAssignment = assignment.getLocationAssignment(guid);
				String location = effectiveAssignment.name;
				AssignmentAttempt attempt = getCachedAttempt(guid, effectiveAssignment);
				if (attempt == null) {
					attempt = getCachedAttempt(guid, assignment.None);
					if (attempt != null) {
						location = assignment.None.name;
					}
				}
				if (attempt == null && assignment.prequel != null) {
					attempt = getCachedAttempt(guid, assignment.prequel);
					if (attempt != null) {
						location = assignment.prequel.name;
					}
				}

				int rowID = -1;
				Integer overrideRowID = assignment.getSubmittedRow(guid);
				if (overrideRowID != null) {
					rowID = overrideRowID;
				} else if (attempt != null) {
					String lastCode = null;
					for (AttemptAction action : attempt) {
						if (action.snapshot != null) {
							lastCode = action.snapshot.toCode();
						}
						if (submittedCode.equals(lastCode)) {
							rowID = action.id;
							if (AttemptAction.IDE_EXPORT_PROJECT.equals(action.message)) {
								break;
							}
						}
					}
					if (rowID == -1) {
						System.out.printf("Submitted code not found for: %s/%s (%s)\n",
								location, guid, file.getPath());
//						if (lastCode != null) System.out.println(diff(lastCode, submittedCode));
					}
				} else {
					location = "";
					if (!noGUID) {
						System.err.printf("No logs found for: %s/%s (%s)\n",
								location, guid, file.getPath());
					}
				}

				output.printf("%s,%s,%d\n", guid, location, rowID);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		output.close();
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
		for (Assignment assignment : dataset.all) {
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
					Submission submissions = new Submission(location.length() == 0 ? null : location,
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

	public static String diff(String a, String b) {
		String[] original = a.split("\n");
		Patch<String> diff = DiffUtils.diff(
				Arrays.asList(original), Arrays.asList(b.split("\n")));
		List<Delta<String>> deltas = diff.getDeltas();
		String out = "";
		int lastChunkEnd = -1;
		int margin = 3;
		boolean[] printed = new boolean[original.length];
		for (Delta<String> delta : deltas) {
			Chunk<String> chunk = delta.getOriginal();
			int chunkStart = chunk.getPosition();
			int chunkEnd = chunkStart + chunk.getLines().size() - 1;
			if (lastChunkEnd >= 0) {
				int printStop = Math.min(lastChunkEnd + margin + 1, chunkStart - 1);
				for (int i = lastChunkEnd + 1; i <= printStop; i++) {
					out += "  " + original[i] + "\n";
					printed[i] = true;
				}
				if (printStop < chunkStart - 1) {
					out += "...\n";
				}
			}

			for (int i = Math.max(chunkStart - margin, 0); i < chunkStart; i++) {
				if (!printed[i]) {
					out += "  " + original[i] + "\n";
					printed[i] = true;
				}
			}

			for (String deleted : delta.getOriginal().getLines()) {
				out += "- " + deleted + "\n";
			}
			for (String added : delta.getRevised().getLines()) {
				out += "+ " + added + "\n";
			}

			for (int i = chunkStart; i <= chunkEnd; i++) printed[i] = true;

			lastChunkEnd = chunkEnd;
		}

		if (lastChunkEnd >= 0) {
			int printStop = Math.min(lastChunkEnd + margin + 1, original.length - 1);
			for (int i = lastChunkEnd + 1; i <= printStop; i++) {
				out += "  " + original[i] + "\n";
				printed[i] = true;
			}
			if (printStop < original.length - 1) {
				out += "...\n";
			}
		}
		return out;
	}
}
