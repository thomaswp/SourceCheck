package com.snap.parser;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
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
			CSVParser parser = new CSVParser(new FileReader(dataDir + FILE), CSVFormat.DEFAULT);
			
			int left = 100;
			for (CSVRecord csvRecord : parser) {
				System.out.println(csvRecord.size());
				if (left-- == 0) break;
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return snapshots;
	}
	
	public static void main(String[] args) {
		SnappierParser.parse("../data/");
	}
}
