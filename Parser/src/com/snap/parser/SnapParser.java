package com.snap.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Snap parser for logging files
 * @author DraganLipovac
 */
public class SnapParser {
	
	private static final String FILE_HEADER = "id,time,message,jsonData,assignmentID,projectID,sessionID,browserID,code \n";
	private static final String NEW_LINE = "\n";
	private Map<String, BufferedWriter> writers = new HashMap<String, BufferedWriter>();
	
	/**
	 * SnapParserConstructor
	 */
	public SnapParser(){
		
	}
	
	/**
	 * Scans the CSV file name, and sends scanner to the processRows method
	 * @param SnapCSVfileName
	 * @throws IOException
	 */
	public void parseStudentRecords(String SnapCSVfileName) throws IOException{
		try{
			Scanner input = new Scanner(new File(SnapCSVfileName)); //filename
			processRows(input);
		} catch (FileNotFoundException e){
			e.printStackTrace();
		}
	}

	/**
	 * takes in scanner, sends assignmentID, projectID, and row to the createFolderStructure method
	 * @param input
	 * @throws IOException
	 */
	private void processRows(Scanner input) throws IOException{
		while(input.hasNextLine()){
			String row = input.nextLine();
			String[] data = row.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1); 
			//create variables
			String assignmentID = data[4];
			String projectID = data[5];
			createFolderStructure(assignmentID,projectID, row);
		}
		//close out all the writers in hashmap
		cleanUp();
		//close out scanner for csv file
		input.close();
	}

	/**
	 * creates File tree structure
	 * @param assignment
	 * @param userId
	 * @throws IOException 
	 */
	private void createFolderStructure(String assignmentID, String projectID, String row) throws IOException{
		//rows without projectID are skipped. these are the logger.started lines
		if(!projectID.equals("")){
			//check to see if folder for assignment such as GuessLab3 already exists, if not - create folder 
			boolean checkFolder = new File(assignmentID).exists();
			String currentFolderPath = null;
			if(!checkFolder){
				File newAssignmentFolder = new File(assignmentID);
				newAssignmentFolder.mkdir();
				currentFolderPath = newAssignmentFolder.getAbsolutePath();			
			} else {
				File newAssignmentFolder = new File(assignmentID);
				currentFolderPath = newAssignmentFolder.getAbsolutePath();		
			}

			//hashmap for bufferedWriters
			String keyword = assignmentID+projectID;
			BufferedWriter writer = writers.get(keyword);
			if(writer == null){
				writer = new BufferedWriter(new FileWriter(new File(currentFolderPath + "/" + projectID + ".csv"),true));
				writers.put(keyword, writer);
				writer.write(FILE_HEADER);
			}
			writer.write(row);
			writer.write(NEW_LINE);
		}
	}
	
	/**
	 * code taken from StackOverflow to close out BufferedWriters
	 */
	private void cleanUp(){
		Set<String> keySet = writers.keySet();
		for(String key : keySet){
			BufferedWriter writer = writers.get(key);
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
}




