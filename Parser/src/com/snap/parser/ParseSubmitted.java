package com.snap.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.snap.data.Snapshot;
import com.snap.parser.Store.Mode;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class ParseSubmitted {

	public static void main(String[] args) throws IOException {
//		for (Assignment assignment : Assignment.Spring2016.All) {
//			parse(assignment);
//		}
		parse(Assignment.Spring2016.LightsCameraAction);
//		System.out.println(diff("a\nb\nc", "a\nd\ne"));
	}

	public static void printToGrade(Assignment assignment) throws IOException {
		Map<String, String> submittedHashes = getOrParseSubmittedHashes(assignment);
		if (submittedHashes == null) throw new RuntimeException("No submitted assignments.");

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Overwrite, true);
		System.out.println("\n\n");
		for (String attemptID : attempts.keySet()) {
			AssignmentAttempt attempt = attempts.get(attemptID);
			if (attempt.submittedActionID >= 0) {
				System.out.printf("%s,%s\n", attempt.submittedSnapshot.guid,
						attempt.submittedActionID);
				submittedHashes.remove(attempt.submittedSnapshot.guid);
			}
		}
		System.out.println("------------------,");
		for (String attemptID : submittedHashes.keySet()) {
			System.out.println(attemptID + ",");
		}

	}

	public static void parse(Assignment assignment) throws IOException {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, true);
		File dir = new File(assignment.submittedDir());
		if (!dir.exists()) {
			System.err.println("Missing submitted directory: " + dir.getAbsolutePath());
			return;
		}
		String outPath = assignment.submittedDir() + ".txt";
		PrintWriter output = new PrintWriter(outPath);
		Set<String> submitted = new HashSet<String>();
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
				if (guid == null || guid.length() == 0) {
					guid = file.getName();
					guid = "nolog-" + guid.substring(0, guid.length() - 4);
				}

				if (!submitted.add(guid)) {
					throw new RuntimeException(String.format("Duplicate submission (%s): (%s)\n",
							guid, file.getAbsolutePath()));
				}

				String submittedCode = snapshot.toCode();

				int rowID = -1;
				// TODO: This should be standardized, but I'm concerned it will break code somewhere
				AssignmentAttempt attempt = attempts.get(guid + ".csv");
				if (attempt != null && attempt.exported) {
					String lastCode = null;
					for (AttemptAction action : attempt) {
						lastCode = action.snapshot.toCode();
						if (submittedCode.equals(lastCode)) {
							lastCode = null;
							rowID = action.id;
							break;
						}
					}
					if (lastCode != null) {
						System.out.printf("Submitted code not found for: %s (%s)\n",
								guid, file.getPath());
						System.out.println(diff(lastCode, submittedCode));
					}
				}

				output.printf("%s,%d\n", guid, rowID);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		output.close();
	}

	public static Map<String, String> getOrParseSubmittedHashes(Assignment assignment)
			throws IOException {
		Map<String, String> submittedHashes = getSubmittedHashes(assignment);
		if (submittedHashes != null) return submittedHashes;
		parse(assignment);
		return getSubmittedHashes(assignment);
	}

	// TODO: Update to return integers and modify parse code to use them
	public static Map<String, String> getSubmittedHashes(Assignment assignment) {
		Map<String, String> submitted = new HashMap<String, String>();
		File file = new File(assignment.submittedDir() + ".txt");
		if (!file.exists()) return null;
		Scanner sc;
		try {
			sc = new Scanner(file);
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.length() > 0) {
					String[] parts = line.split(",");
					if (parts.length != 2) {
						sc.close();
						throw new RuntimeException("Invalid line: " + line);
					}
					submitted.put(parts[0], parts[1]);
				}
			}
			sc.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		return submitted;
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
//			System.out.println(chunk);
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
