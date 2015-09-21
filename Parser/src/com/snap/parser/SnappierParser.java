package com.snap.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public class SnappierParser {
	
	public final static String FILE = "snappier/snappier.csv";
	public final static String SPLIT_FOLDER = "snappier/split/";
	private final static int MAX_WRITERS = 20;
	
	public static void splitCSV(String dataDir) {
		
		if (!dataDir.endsWith("/") && !dataDir.endsWith("\\")) {
			dataDir += "/";
		}
		try {			
			File splitFolder = new File(dataDir + SPLIT_FOLDER);
			if (splitFolder.exists()) {
				for (File f : splitFolder.listFiles()) f.delete();
			}
			splitFolder.delete();
			splitFolder.mkdirs();
			
			CSVParser parser = new CSVParser(new FileReader(dataDir + FILE), CSVFormat.DEFAULT.withHeader());
			
			
			HashMap<String, CSVPrinter> printers = new HashMap<String, CSVPrinter>();
			LinkedHashSet<String> openFiles = new LinkedHashSet<String>();
						
			int[] keepIndices = new int[] {
					4, 5, 7, 9
			};
			String[] fullHeader = parser.getHeaderMap().keySet().toArray(new String[parser.getHeaderMap().keySet().size()]);
			String[] header = new String[keepIndices.length];
			for (int i = 0; i < header.length; i++) header[i] = fullHeader[keepIndices[i]];
			
			int row = 0;
			for (CSVRecord csvRecord : parser) {
				if (++row % 250 == 0) System.out.println(row);
				int size = csvRecord.size();
				if (size != 10) System.out.println("Improper size: " + csvRecord.getRecordNumber());
//				for (int i = 0; i < size; i++) {
//					System.out.println(csvRecord.get(i));
//				}
				
				String studentID = csvRecord.get(1);
//				String pairID = csvRecord.get(2);
				String projectName = csvRecord.get(3);
				
//				if (pairID.length() > 0) System.out.println("pair: " + pairID);
				
				String filename = new File(splitFolder, projectName + "_" + studentID + ".csv").getAbsolutePath();
				CSVPrinter printer = printers.get(filename);
				if (printer == null) {
					while (openFiles.size() >= MAX_WRITERS) {
						String key = openFiles.iterator().next();
						openFiles.remove(key);
						printers.remove(key).close();
					}
					
					printer = new CSVPrinter(new FileWriter(filename), CSVFormat.EXCEL.withHeader(header));
					printers.put(filename, printer);
				}
				
				openFiles.remove(filename);
				openFiles.add(filename);
				
				Object[] data = new String[keepIndices.length];
				for (int i = 0; i < keepIndices.length; i++) data[i] = csvRecord.get(keepIndices[i]);
				printer.printRecord(data);
			}
			
			parser.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void analyze(String dataDir) {
		File splitFolder = new File(dataDir + SPLIT_FOLDER);
		HashSet<String> students = new HashSet<String>();
		for (File file : splitFolder.listFiles()) {
			String name = file.getName();
			name = name.split("(_|\\.)")[1];
			students.add(name);
		}
		System.out.println(students.size());
	}
	
	public static void main(String[] args) throws FileNotFoundException {
//		SnappierParser.splitCSV("../data/");
		SnappierParser.analyze("../data/");
	}
}
