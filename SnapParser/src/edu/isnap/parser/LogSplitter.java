package edu.isnap.parser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.dataset.Dataset;

/**
 * Class for splitting a large iSnap log into separate folders for each assignment, and separate
 * files for each assignment attempt.
 */
public class LogSplitter {

	// These two setting control how much RAM the process takes up, but not precisely.
	// The larger the RAM buffer, theoretically the better performance (to a point).
	// On most datasets, it looks like these keep the process around 1GB.

	/** Max number of open project files allowed to be written at once */
	public static int MAX_OPEN_PROJECTS = 500;
	/** Longest number of row to keep a project open without any logs before flushing */
	public static int MAX_ROWS_HOLD_OPEN_PROJECT = 3000;

	// File path to project map
	private final Map<String, Project> projectMap = new HashMap<>();
	// Last-written-line to project map, used to flush stale projects
	private final TreeMap<Integer, Project> projectQueue = new TreeMap<>();
	// Headers for the CSV file to be split
	private String[] headers;

	public void splitStudentRecords(Dataset dataset) throws IOException {
		splitStudentRecords(dataset.dataFile, dataset.dataFileCSVFormat());
	}

	/**
	 * Scans the CSV file name, and sends scanner to the processRows method
	 * @param snapCSVfileName
	 * @throws IOException
	 */
	public void splitStudentRecords(String file, CSVFormat format) throws IOException {
		String outputFolder = file.substring(0, file.lastIndexOf(".")) + "/parsed";
		new File(outputFolder).mkdirs();

		// Open the BIG CSV file and read the headers
		CSVParser parser = new CSVParser(new FileReader(file), format);
		Map<String, Integer> headerMap = parser.getHeaderMap();
		headers = new String[headerMap.size()];
		for (Entry<String, Integer> entry : headerMap.entrySet()) {
			headers[entry.getValue()] = entry.getKey();
		}
		boolean hasUserID = headerMap.containsKey("userID");
		int i = 0;

		// Write each record to the appropriate split-out CSV file
		System.out.println("Splitting records:");
		for (CSVRecord record : parser) {
			String projectID = record.get("projectID");

			// rows without projectID are skipped, e.g. Logger.started lines
			if(projectID.equals("")) continue;

			String assignmentID = record.get("assignmentID");
			String userID = hasUserID ? record.get("userID") : null;
			writeRecord(i, assignmentID, projectID, userID, outputFolder, record);

			// Flush the oldest writer if its stale or we have too many open
			int oldest = projectQueue.firstKey();
			if (oldest < i - MAX_ROWS_HOLD_OPEN_PROJECT ||
					projectQueue.size() > MAX_OPEN_PROJECTS) {
				projectQueue.remove(oldest).flush();
			}

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
	private void writeRecord(int line, String assignmentID, String projectID, String userID,
			String outputFolder, CSVRecord record) throws IOException {
		// Create the appropriate folder if it does not exist
		File newAssignmentFolder = new File(outputFolder, assignmentID);
		newAssignmentFolder.mkdir();
		String currentFolderPath = newAssignmentFolder.getAbsolutePath();

		// Get the project from the Map or create it if it does not exist
		String keyword = assignmentID + projectID + userID;
		Project project = projectMap.get(keyword);
		if(project == null){
			String filename = projectID;
			if (userID != null) filename += "_" + userID.substring(0, Math.min(8, userID.length()));
			filename = filename.replaceAll("[^a-zA-Z0-9\\._-]+", "_");
			project = new Project(currentFolderPath + "/" + filename + ".csv",
					CSVFormat.EXCEL.withHeader(headers));
			projectMap.put(keyword, project);
		}
		// Write the record (in memory)
		project.writeRecord(record);

		// Update the last line written to this project, so we know it's not stale
		int lastLine = project.lastLine;
		if (lastLine >= 0) projectQueue.remove(lastLine);
		project.lastLine = line;
		projectQueue.put(line, project);
	}

	private void cleanUpSplit() throws IOException{
		System.out.println("\nCleaning up...");
		for (Project project : projectQueue.values()) {
			project.flush();
		}
	}

	// Represents one projectID/assignmentID combination
	private static class Project {
		private final CSVPrinter printer;
		private final StringWriter writer;
		private final String path;

		public int lastLine = -1;

		public Project(String path, CSVFormat format) throws IOException {
			this.path = path;
			// We write to a StringWriter using a CSVPrinter
			writer = new StringWriter();
			printer = new CSVPrinter(writer, format);

			// Delete any old version of this file if it exists
			File file = new File(path);
			if (file.exists()) file.delete();
		}

		public void writeRecord(CSVRecord record) throws IOException {
			Object[] cols = new Object[record.size()];
			for (int i = 0; i < cols.length; i++) {
				cols[i] = record.get(i);
			}
			printer.printRecord(cols);
		}

		public void flush() throws IOException {
			// Write the buffer to a file, being sure to append
			FileWriter fileWriter = new FileWriter(path, true);
			fileWriter.write(writer.toString());
			fileWriter.close();
			// Reset the bugger and deallocate it
			writer.getBuffer().setLength(0);
			writer.getBuffer().trimToSize();
		}
	}
}
