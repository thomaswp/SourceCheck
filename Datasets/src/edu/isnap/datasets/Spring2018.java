package edu.isnap.datasets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser.Filter;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

//TODO: Check for students starting on different assignments
public class Spring2018 extends Dataset {

	public final static Date start = Assignment.date(2018, 1, 6);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/spring2018";
	public final static String dataFile = dataDir + ".csv";
	public final static Spring2018 instance = new Spring2018();

	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2018, 1, 24), true) {
	};

	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", Assignment.date(2018, 1, 24), true, false, null) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("ae29ae0b-1dca-4f55-8ed7-e152d38f127d".equals(attemptID)) {
				return LightsCameraAction;
			}
			return super.getLocationAssignment(attemptID);
		}
	};

	public final static Assignment SquiralObjectives = new Assignment(instance,
			"squiralHW", Assignment.date(2018, 1, 30), true, false, null) {
		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("14e8cfe1-819f-41ac-bba7-c34b08bf6bc7".equals(attemptID)) {
				return PolygonMaker;
			}
			return super.getLocationAssignment(attemptID);
		}
		@Override
		public boolean ignore(String attemptID) {
			// The log length is less than 30, the student just submit a custom block which draws a square
			return "c1b864fc-18ee-44ed-b1d5-6c1bf858434d".equals(attemptID);
		}
		@Override
		public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly, boolean addMetadata,
				Filter... filters) {

			// The objective feature creates two IDEs, so the students' log information is stored in the first IDE,
			// but their assignment projectID points another IDE. Need to merge the log information of both files before
			// searching students' submission.
			try {
				mergeLogFiles(this);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return super.load(mode, snapshotsOnly, addMetadata, filters);
		};

		// The log contains two projects' ID. Copy the log and create a new file with another projectID.
		private void copyLogFiles(Assignment assignment) throws FileNotFoundException, IOException {
			File assignmentDir = new File(assignment.parsedDir());
			CSVFormat format = assignment.dataset.dataFileCSVFormat();
			System.out.println("rename records");
			for (File file : assignmentDir.listFiles()) {
				if (!file.getName().endsWith(".csv")) continue;

				CSVParser parser = new CSVParser(new FileReader(file), format);

				String newFileName = "";
				// Get the new file name
				for (CSVRecord record : parser) {
					String codeXML = record.get("code");
					// rows without code are skipped, e.g. Logger.started lines
					if(codeXML.equals("")) continue;

					String projectID = record.get("projectID");
					if(projectID.equals("")) continue;

					//parse the code to get another projectID
					Snapshot curss = Snapshot.parse("", codeXML);
					String objProjectID = curss.guid;
					if (objProjectID.equals(projectID)) continue;

					newFileName = objProjectID;
					newFileName = newFileName.replaceAll("[^a-zA-Z0-9\\._-]+", "_");
					break;
				}
				parser.close();

				// Copy the parsed CSV file and rename it to another projectID
				if (!newFileName.equals("")) {
					File newFile = new File(assignment.parsedDir() + "/copied/" + newFileName + ".csv");
					if (newFile.exists()) {
						continue;
					}
					InputStream fileInput = null;
				    OutputStream fileOutput = null;
				    try {
				    	fileInput = new FileInputStream(file);
				    	fileOutput = new FileOutputStream(newFile, false);
				        byte[] buffer = new byte[1024];
				        int length;
				        while ((length = fileInput.read(buffer)) > 0) {
				        	fileOutput.write(buffer, 0, length);
				        }
				    } finally {
				    	fileInput.close();
				    	fileOutput.close();
				    }
				}
				System.out.print(".");
			}
		}


		// Merge the newly created log files with the original log files
		private void mergeLogFiles(Assignment assignment) throws FileNotFoundException, IOException {
				File assignmentDir = new File(assignment.parsedDir());
				if (!assignmentDir.exists()) {
					throw new RuntimeException("Assignment has not been added and parsed: " + assignment);
				}
				new File(assignment.parsedDir()+"/copied").mkdirs();
				new File(assignment.parsedDir()+"/merged").mkdirs();

				copyLogFiles(assignment);
				System.out.println();
				// If the file has the same file name with copied files, then merge, otherwise just copy the file.
				CSVFormat format = assignment.dataset.dataFileCSVFormat();

				System.out.println("Merge records");
				for (File file : assignmentDir.listFiles()) {
					if (!file.getName().endsWith(".csv")) continue;
					String originalFileName = file.getName();
					// Copy CSV file and rename it to another projectID
					if (!originalFileName.equals("")) {
						File copiedFile = new File(assignment.parsedDir() + "/copied/" + originalFileName);
						CSVParser originalFileParser = new CSVParser(new FileReader(file), format);
						List<CSVRecord> originalRecords = originalFileParser.getRecords();
						Spreadsheet newFile = new Spreadsheet();
						if (copiedFile.exists()) {
							CSVParser copiedFileParser = new CSVParser(new FileReader(copiedFile), format);
							// Combine two file's content to the new file
							List<CSVRecord> copiedRecords = copiedFileParser.getRecords();

							int originalIdx = 1, copiedIdx = 1;

							while(originalIdx < originalRecords.size() && copiedIdx < copiedRecords.size()) {
								if (originalRecords.get(originalIdx).get("id").equals("id")) {
									originalIdx++;
								}
								if (copiedRecords.get(copiedIdx).get("id").equals("id")) {
									copiedIdx++;
								}
								if (Integer.parseInt(originalRecords.get(originalIdx).get("id"))
									< Integer.parseInt(copiedRecords.get(copiedIdx).get("id"))){
									addNewRow(newFile, originalRecords, originalIdx);
									originalIdx++;
								} else {
									addNewRow(newFile, copiedRecords, copiedIdx);
									copiedIdx++;
								}
							}

							while (originalIdx < originalRecords.size()) {
								addNewRow(newFile, originalRecords, originalIdx);
								originalIdx++;
							}

							while (copiedIdx < copiedRecords.size()) {
								addNewRow(newFile, copiedRecords, copiedIdx);
								copiedIdx++;
							}
							newFile.write(assignment.parsedDir() + "/merged/" + originalFileName);
							copiedFileParser.close();
							originalFileParser.close();
						}
					}
					System.out.print(".");
				}

				//overwrite the original parsed file
				System.out.println();
				System.out.print("Overwrite records");
				File mergedDir = new File(assignment.parsedDir() + "/merged/");
				for (File file : mergedDir.listFiles()) {
					if (!file.getName().endsWith(".csv")) continue;

					String newFileName = file.getName();
					if (!newFileName.equals("")) {
						File newFile = new File(assignment.parsedDir() + "/" + newFileName);
						InputStream fileInput = null;
					    OutputStream fileOutput = null;
					    try {
					    	fileInput = new FileInputStream(file);
					    	fileOutput = new FileOutputStream(newFile, false);
					        byte[] buffer = new byte[1024];
					        int length;
					        while ((length = fileInput.read(buffer)) > 0) {
					        	fileOutput.write(buffer, 0, length);
					        }
					    } finally {
					    	fileInput.close();
					    	fileOutput.close();
					    }
					}
					System.out.print(".");
				}
			}

		private void addNewRow(Spreadsheet newFile, List<CSVRecord> records, int index) {
			newFile.newRow();
			newFile.put("id", records.get(index).get("id"));
			newFile.put("time", records.get(index).get("time"));
			newFile.put("message", records.get(index).get("message"));
			newFile.put("data", records.get(index).get("data"));
			newFile.put("assignmentID", records.get(index).get("assignmentID"));
			newFile.put("userID", records.get(index).get("userID"));
			newFile.put("sessionID", records.get(index).get("sessionID"));
			newFile.put("browserID", records.get(index).get("browserID"));
			newFile.put("code", records.get(index).get("code"));
		}
	};

	public final static Assignment Pong1 = new Assignment(instance,
			"pong1Lab", Assignment.date(2018, 1, 30), true, false, null) {
	};

	public final static Assignment Pong2 = new Assignment(instance,
			"pong2HW", Assignment.date(2018, 2, 2), true, false, Pong1) {
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab", Assignment.date(2018, 2, 2), true, false, null) {
		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("b86cf4d7-8352-45f6-baba-cad302acc8bc".equals(attemptID)) {
				return Pong1;
			}
			return super.getLocationAssignment(attemptID);
		}

		@Override
		public boolean ignore(String attemptID) {
			return "c3b814f8-54d8-4bcf-9bf3-a7ec7e4518ab".equals(attemptID);
		};
	};

	/**
	 * The assignment changed in Spring 2018, so we've changed the name to GG2 Lab
	 */
	public final static Assignment GuessingGame2Lab = new Assignment(instance,
			"guess2Lab", Assignment.date(2018, 2, 14), true, false, GuessingGame1) {
	};

	public final static Assignment Project = new Assignment(instance,
			"project", Assignment.date(2018, 2, 26), true, false, null) {
	};

	public final static Assignment[] All = {
		LightsCameraAction, PolygonMaker, SquiralObjectives,
		Pong1, Pong2, GuessingGame1, GuessingGame2Lab
	};

	private Spring2018() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}