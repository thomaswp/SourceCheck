package com.snap.parser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Class for splitting a large iSnap log into separate folders for each assignment, and separate
 * files for each assignment attempt.
 */
public class LogSplitter {

	public static void main(String[] args) throws IOException {
		// Replace "Fall2015" with the dataset you want to load
		splitStudentRecords(Assignment.Fall2015.dataFile);
	}

	// Header for iSnap log table
	private static final String[] HEADER = new String[] {
			"id","time","message","jsonData","assignmentID","projectID","sessionID","browserID",
			"code"
	};

	private final static Map<String, CSVPrinter> csvPrinters = new HashMap<String, CSVPrinter>();

	/**
	 * Scans the CSV file name, and sends scanner to the processRows method
	 * @param snapCSVfileName
	 * @throws IOException
	 */
	public static void splitStudentRecords(String file) throws IOException{
		String outputFolder = file.substring(0, file.lastIndexOf(".")) + "/parsed";
		new File(outputFolder).mkdirs();
		CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withHeader());
		int i = 0;
		System.out.println("Splitting records:");
		for (CSVRecord record : parser) {
			String assignmentID = record.get(4);
			String projectID = record.get(5);
			writeRecord(assignmentID,projectID, outputFolder, record);
			if (i++ % 1000 == 0) System.out.print("+");
			if (i % 50000 == 0) System.out.println();
		}
		//close out all the writers in hashmap
		System.out.println();
		parser.close();
		cleanUpSplit();
	}

	/**
	 * creates File tree structure
	 * @param assignment
	 * @param userId
	 * @throws IOException
	 */
	private static void writeRecord(String assignmentID, String projectID, String outputFolder,
			CSVRecord record) throws IOException{
		// rows without projectID are skipped. these are the logger.started lines
		if(!projectID.equals("")){
			// check to see if folder for assignment such as GuessLab3 already exists
			// if not - create folder
			File newAssignmentFolder = new File(outputFolder, assignmentID);
			newAssignmentFolder.mkdir();
			String currentFolderPath = newAssignmentFolder.getAbsolutePath();

			//hashmap for bufferedWriters
			String keyword = assignmentID+projectID;
			CSVPrinter printer = csvPrinters.get(keyword);
			if(printer == null){
				printer = new CSVPrinter(
						new FileWriter(currentFolderPath + "/" + projectID + ".csv"),
						CSVFormat.EXCEL.withHeader(HEADER));
				csvPrinters.put(keyword, printer);
			}

			Object[] cols = new Object[record.size()];
			for (int i = 0; i < cols.length; i++) {
				cols[i] = record.get(i);
			}
			printer.printRecord(cols);

			if (Math.random() < 0.1) printer.flush();
		}
	}

	private static void cleanUpSplit(){
		System.out.println("Cleaning up:");
		Set<String> keySet = csvPrinters.keySet();
		int i = 0;
		for(String key : keySet){
			CSVPrinter writer = csvPrinters.get(key);
			if(writer != null){
				try{
					writer.close();
				} catch (Throwable t){
					t.printStackTrace();
				}
				writer = null;
			}
			if (i++ % 10 == 0) System.out.print("+");
			if (i % 500 == 0) System.out.println();
		}
		csvPrinters.clear();
	}
}
