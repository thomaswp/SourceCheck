package com.snap.parser;

import java.io.File;
import java.io.FileNotFoundException;
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
 * Snap parser for logging files
 * @author DraganLipovac
 */
public class SnapParser {
	
	private static final String[] HEADER = new String[] {
			"id","time","message","jsonData","assignmentID","projectID","sessionID","browserID","code"
	};
	private Map<String, CSVPrinter> csvPrinters = new HashMap<String, CSVPrinter>();
	private String outputFolder;
	
	/**
	 * SnapParserConstructor
	 */
	public SnapParser(){
		
	}
	
	/**
	 * Scans the CSV file name, and sends scanner to the processRows method
	 * @param snapCSVfileName
	 * @throws IOException
	 */
	public void parseStudentRecords(String snapCSVfileName, String outputFolder) throws IOException{
		this.outputFolder = outputFolder;
		new File(outputFolder).mkdirs();
		try{
			processRows(snapCSVfileName);
		} catch (FileNotFoundException e){
			e.printStackTrace();
		}
	}

	/**
	 * takes in scanner, sends assignmentID, projectID, and row to the createFolderStructure method
	 * @param input
	 * @throws IOException
	 */
	private void processRows(String input) throws IOException{
		CSVParser parser = new CSVParser(new FileReader(input), CSVFormat.DEFAULT.withHeader());
		for (CSVRecord record : parser) {
			String assignmentID = record.get(4);
			String projectID = record.get(5);
			createFolderStructure(assignmentID,projectID, record);
		}
		//close out all the writers in hashmap
		parser.close();
		cleanUp();
		//close out scanner for csv file
	}

	/**
	 * creates File tree structure
	 * @param assignment
	 * @param userId
	 * @throws IOException 
	 */
	private void createFolderStructure(String assignmentID, String projectID, CSVRecord record) throws IOException{
		//rows without projectID are skipped. these are the logger.started lines
		if(!projectID.equals("")){
			//check to see if folder for assignment such as GuessLab3 already exists, if not - create folder
			File newAssignmentFolder = new File(outputFolder, assignmentID);
			newAssignmentFolder.mkdir();
			String currentFolderPath = newAssignmentFolder.getAbsolutePath();

			//hashmap for bufferedWriters
			String keyword = assignmentID+projectID;
			CSVPrinter printer = csvPrinters.get(keyword);
			if(printer == null){
				printer = new CSVPrinter(new FileWriter(currentFolderPath + "/" + projectID + ".csv"), CSVFormat.EXCEL.withHeader(HEADER));
				csvPrinters.put(keyword, printer);
			}
			
			Object[] cols = new Object[record.size()];
			for (int i = 0; i < cols.length; i++) {
				cols[i] = record.get(i);
			}
			printer.printRecord(cols);
		}
	}
	
	/**
	 * code taken from StackOverflow to close out BufferedWriters
	 */
	private void cleanUp(){
		Set<String> keySet = csvPrinters.keySet();
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
		}
	}
	
	public static void main(String[] args) throws IOException {
		new SnapParser().parseStudentRecords("../data/csc200/fall2015.csv", "../data/csc200/fall2015");
	}
}




