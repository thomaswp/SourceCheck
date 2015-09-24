package com.snap.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Snap parser for logging files
 * @author DraganLipovac
 */
public class SnapParser {
	
	private static final String[] HEADER = new String[] {
			"id","time","message","jsonData","assignmentID","projectID","sessionID","browserID","code"
	};
	private final Map<String, CSVPrinter> csvPrinters = new HashMap<String, CSVPrinter>();
	private final String outputFolder;
	
	/**
	 * SnapParserConstructor
	 */
	public SnapParser(String outputFolder){
		this.outputFolder = outputFolder;
		new File(outputFolder).mkdirs();
	}
	
	/**
	 * Scans the CSV file name, and sends scanner to the processRows method
	 * @param snapCSVfileName
	 * @throws IOException
	 */
	public void splitStudentRecords(String snapCSVfileName) throws IOException{
		CSVParser parser = new CSVParser(new FileReader(snapCSVfileName), CSVFormat.DEFAULT.withHeader());
		for (CSVRecord record : parser) {
			String assignmentID = record.get(4);
			String projectID = record.get(5);
			writeRecord(assignmentID,projectID, record);
		}
		//close out all the writers in hashmap
		parser.close();
		cleanUpSplit();
	}
	
	/**
	 * creates File tree structure
	 * @param assignment
	 * @param userId
	 * @throws IOException 
	 */
	private void writeRecord(String assignmentID, String projectID, CSVRecord record) throws IOException{
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
	private void cleanUpSplit(){
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
	
	Kryo kyro = new Kryo();
	
	private List<DataRow> parseRows(File logFile) throws IOException {
		File cached = new File(logFile.getAbsolutePath() + ".cached");
		if (cached.exists()) {
			try {
				Input input = new Input(new FileInputStream(cached));
				@SuppressWarnings("unchecked")
				List<DataRow> rows = kyro.readObject(input, LinkedList.class);
				input.close();
				if (rows != null) return rows;
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}
		
		ArrayList<DataRow> rows = new ArrayList<>();
		CSVParser parser = new CSVParser(new FileReader(logFile), CSVFormat.EXCEL.withHeader());
		
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		for (CSVRecord record : parser) {
			String timestampString = record.get(1);
			Date timestamp = null;
			try {
				timestamp = format.parse(timestampString);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			String xml = record.get(8);
			DataRow row = new DataRow(timestamp, xml);
			if (row.snapshot != null) {
				rows.add(row);
//				System.out.println(row.snapshot.toCode());
			}
		}
		parser.close();
		
		cached.delete();
		try {
			Output output = new Output(new FileOutputStream(cached));
			kyro.writeObject(output, rows);
			output.close();
		} catch (Exception e) { }
		
		System.out.println("Parsed: " + rows.size());
		return rows;
	}
	
	
	private HashMap<String, List<DataRow>> parseAssignment(String folder) throws IOException {
		HashMap<String, List<DataRow>> students = new HashMap<>();
		for (File file : new File(outputFolder, folder).listFiles()) {
			if (file.getName().endsWith(".cached")) continue;
			List<DataRow> rows = parseRows(file);
			if (rows.size() > 3) {
				students.put(file.getName(), rows);
			}
		}
		return students;
	}
	
	
	public static void main(String[] args) throws IOException {
		SnapParser parser = new SnapParser("../data/csc200/fall2015");
//		parser.splitStudentRecords("../data/csc200/fall2015.csv");
		
//		parser.parseRows(new File(parser.outputFolder + "/guess1Lab/0b368197-7d2d-4b11-be38-9111bbb9b475.csv"));
		
//		parser.parseRows(new File(parser.outputFolder + "/guess1Lab/0e1c3e4a-ecb5-4576-bde7-ace5141f9a5b.csv"));
		parser.parseAssignment("guess1Lab");
	}
}




