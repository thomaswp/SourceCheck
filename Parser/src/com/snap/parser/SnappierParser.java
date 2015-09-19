package com.snap.parser;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.snap.data.Snapshot;

public class SnappierParser {
	
	public final static String FILE = "snappier/snappier.csv";
	
	public static List<Snapshot> parse(String dataDir) {
		List<Snapshot> snapshots = new ArrayList<>();
		
		if (!dataDir.endsWith("/") && !dataDir.endsWith("\\")) {
			dataDir += "/";
		}
		try {			
			CSVParser parser = new CSVParser(new FileReader(dataDir + FILE), CSVFormat.DEFAULT.withHeader());
			
			for (String header : parser.getHeaderMap().keySet()) System.out.print(header + ", ");
			System.out.println();
			
			HashSet<String> students = new HashSet<String>();
			HashSet<String> projects = new HashSet<String>();
			
			int left = 20000;
			for (CSVRecord csvRecord : parser) {
				if (left-- == 0) break;
				int size = csvRecord.size();
//				for (int i = 0; i < size; i++) {
//					System.out.println(csvRecord.get(i));
//				}
				
				String studentID = csvRecord.get(1);
				String pairID = csvRecord.get(2);
				String projectName = csvRecord.get(3);
				
				students.add(studentID);
				projects.add(projectName);				
				
//				for (int i = 0; i < 7; i++) System.out.print(csvRecord.get(i) + ", ");
//				System.out.println();
			}
			System.out.println("Students: " + students.size());
			System.out.println("Projects: " + projects.size());
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return snapshots;
	}
	
	public static void main(String[] args) {
		SnappierParser.parse("../data/");
	}
}
