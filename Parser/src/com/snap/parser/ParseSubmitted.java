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

public class ParseSubmitted {

	public static void main(String[] args) throws IOException {
		for (Assignment assignment : Fall2015.All) {
			parse(assignment);
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
				if (submitted.add(snapshot.guid)) {
					output.printf("%s,%x\n", snapshot.guid, snapshot.toCode().hashCode());
				} else {
					System.err.println("Duplicate submission: " + file.getAbsolutePath());
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