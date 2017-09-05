package edu.isnap.dataset;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import org.apache.commons.csv.CSVRecord;

public class Grade {

	public final String id;
	public final int gradedRow;
	public final boolean outlier;
	public final LinkedHashMap<String, Integer> tests = new LinkedHashMap<>();

	public final static Integer FAIL = 2, ATTEMPT = 1, PASS = 2;

    private final static Set<String> NonTestColumns = new HashSet<>();
    static {
        NonTestColumns.add("rowNum");
        NonTestColumns.add("Project ID");
        NonTestColumns.add("Extra code");
        NonTestColumns.add("Graded ID");
        NonTestColumns.add("Outlier");
    }

	@SuppressWarnings("unused")
	private Grade() {
		id = null;
		gradedRow = -1;
		outlier = false;
	}

	public Grade(CSVRecord record, String[] header) {
		boolean outlier = false;

		try {
			id = record.get("Project ID");
			gradedRow = Integer.parseInt(record.get("Graded ID"));
			if (record.isSet("Outlier") && "yes".equalsIgnoreCase(record.get("Outlier"))) {
				outlier = true;
				return;
			}

			for (int i = 0; i < header.length; i++) {
				String key = header[i];
				if (NonTestColumns.contains(key)) continue;

				String value = record.get(i);
				if (value.length() == 0) {
					outlier = true;
					tests.clear();
					return;
				}

				int grade;
				if ("pass".equalsIgnoreCase(value)) {
					grade = 2;
				} else if ("fail".equalsIgnoreCase(value)) {
					grade = 0;
				} else {
					grade = Integer.parseInt(value);
				}

				tests.put(key, grade);
			}
		} finally {
			this.outlier = outlier;
		}
	}

	public boolean passed(String test) {
		return tests.containsKey(test) && tests.get(test) == PASS;
	}

	public double average() {
		if (tests.size() == 0) return 0;
		int passed = 0;
		for (Integer test : tests.values()) {
			if (test == PASS) passed++;
		}
		return (double) passed / tests.size();
	}

	public double partialAverage() {
		if (tests.size() == 0) return 0;
		int points = 0;
		for (Integer test : tests.values()) {
			points += test;
		}
		return (double) points / (tests.size() * PASS);
	}
}
