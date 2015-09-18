package com.snap.parser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.snap.data.Snapshot;

public class SnappierParser {
	
	public final static String FILE = "snappier/snappier.csv";
	
	public static List<Snapshot> parse(String dataDir) {
		List<Snapshot> snapshots = new ArrayList<>();
		
		if (!dataDir.endsWith("/") && !dataDir.endsWith("\\")) {
			dataDir += "/";
		}
		try {
			FileInputStream fis = new FileInputStream(dataDir + FILE);
			Scanner sc = new Scanner(fis);
			
			for (int i = 0; i < 10; i++) {
				String line = sc.nextLine();
				String[] parts = line.split(",");
//				String.join(",", elements)
				System.out.println(line);
			}
			
			sc.close();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		return snapshots;
	}
	
	public static void main(String[] args) {
		SnappierParser.parse("../data/");
	}
}
