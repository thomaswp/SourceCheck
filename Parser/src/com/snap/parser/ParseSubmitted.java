package com.snap.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.snap.data.Snapshot;
import com.snap.parser.Assignment.Fall2015;
import com.snap.parser.Store.Mode;

public class ParseSubmitted {

	public static void main(String[] args) throws IOException {
		for (Assignment assignment : Fall2015.All) {
			parse(assignment);
		}
		printToGrade(Fall2015.GuessingGame2);
	}

	public static void printToGrade(Assignment assignment) {
		Map<String, String> submittedHashes = getSubmittedHashes(assignment);
		if (submittedHashes == null) System.out.println("No submitted assignments.");

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
				String guid = snapshot.guid;
				if (guid == null || guid.length() == 0) {
					guid = file.getName();
					guid = "nolog-" + guid.substring(0, guid.length() - 4);
				}
				if (submitted.add(guid)) {
					System.out.printf("%s => %s\n", file.getName(), guid);
					output.printf("%s,%x\n", guid, snapshot.toCode().hashCode());
				} else {
					System.err.printf("Duplicate submission (%s): (%s)\n", guid, file.getAbsolutePath());
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		output.close();
	}

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
}
