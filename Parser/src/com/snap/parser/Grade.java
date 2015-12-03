package com.snap.parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.csv.CSVRecord;

public class Grade {

	public final String id;
	public final String gradedID;
	public final boolean extraCode;
	public final boolean outlier;
	public final HashMap<String, Boolean> tests = new LinkedHashMap<String, Boolean>();
	
	private final static Set<String> NonTestColumns = new HashSet<String>();
	static {
		NonTestColumns.add("rowNum");
		NonTestColumns.add("Project ID");
		NonTestColumns.add("Extra code");
		NonTestColumns.add("Graded ID");
		NonTestColumns.add("Outlier");
	}
	
	@SuppressWarnings("unused")
	private Grade() { 
		id = gradedID = null;
		extraCode = outlier = false;
	}
	
	public Grade(CSVRecord record) {
		id = record.get(1);
		gradedID = record.get(2);
		extraCode = "yes".equalsIgnoreCase(record.get("Extra code"));
		outlier = "yes".equalsIgnoreCase(record.get("Outlier"));

		Map<String, String> map = record.toMap();
		for (Entry<String, String> entry : map.entrySet()) {
			String key = entry.getKey();
			if (NonTestColumns.contains(key)) continue;
			
			String value = entry.getValue();
			boolean pass;
			if ("pass".equalsIgnoreCase(value)) pass = true;
			else if ("fail".equalsIgnoreCase(value)) pass = false;
			else throw new RuntimeException("Unknown test value: " + value);
			
			tests.put(key, pass);
		}
	}
}
