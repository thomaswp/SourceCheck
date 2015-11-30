package com.snap.parser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVRecord;

public class Grade {

	public final String id;
	public final String gradedID;
	public final boolean extraCode;
	public final HashMap<String, Boolean> tests = new LinkedHashMap<String, Boolean>();
	
	@SuppressWarnings("unused")
	private Grade() { 
		id = gradedID = null;
		extraCode = false;
	}
	
	public Grade(CSVRecord record) {
		id = record.get(1);
		gradedID = record.get(2);
		extraCode = "yes".equalsIgnoreCase(record.get("Extra code"));

		Map<String, String> map = record.toMap();
		for (Entry<String, String> entry : map.entrySet()) {
			String key = entry.getKey();
			if ("rowNum".equals(key) || "Project ID".equals(key) || 
					"Extra code".equals(key) || "Graded ID".equals(key)) {
				continue;
			}
			
			String value = entry.getValue();
			boolean pass;
			if ("pass".equalsIgnoreCase(value)) pass = true;
			else if ("fail".equalsIgnoreCase(value)) pass = false;
			else throw new RuntimeException("Unknown test value: " + value);
			
			tests.put(key, pass);
		}
	}
}
