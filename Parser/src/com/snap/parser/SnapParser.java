package com.snap.parser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.snap.data.BlockDefinitionGroup.BlockIndex;
import com.snap.data.Snapshot;
import com.snap.parser.Store.Mode;

/**
 * Snap parser for logging files
 * @author DraganLipovac
 */
public class SnapParser {
	
	private static final String[] HEADER = new String[] {
			"id","time","message","jsonData","assignmentID","projectID","sessionID","browserID","code"
	};
	
	private final static Map<String, CSVPrinter> csvPrinters = new HashMap<String, CSVPrinter>();
	
	private final String outputFolder;
	private final Mode storeMode;
	
	/**
	 * SnapParserConstructor
	 */
	public SnapParser(String outputFolder, Mode cacheUse){
		this.outputFolder = outputFolder;
		this.storeMode = cacheUse;
		new File(outputFolder).mkdirs();
	}
	
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
	private static void writeRecord(String assignmentID, String projectID, String outputFolder, CSVRecord record) throws IOException{
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
			
			if (Math.random() < 0.1) printer.flush();
		}
	}
	
	/**
	 * code taken from StackOverflow to close out BufferedWriters
	 */
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
	
	public SolutionPath parseSubmission(Assignment assignment, String id, boolean snapshotsOnly) throws IOException {
		return parseRows(new File(assignment.dataDir + "/parsed/" + assignment.name, id + ".csv"), null, snapshotsOnly, assignment.start, assignment.end);
	}
	
	private SolutionPath parseRows(final File logFile, final Grade grade, final boolean snapshotsOnly, final Date minDate, final Date maxDate) throws IOException {
		String cachePath = logFile.getAbsolutePath().replace(".csv", "") + (snapshotsOnly ? "" :  "-data");
		int hash = 0;
		if (minDate != null) hash += minDate.hashCode();
		if (maxDate != null) hash += maxDate.hashCode();
		if (hash != 0) cachePath += "-d" + (hash); 
		cachePath += ".cached";
		
		return Store.getCachedObject(new Kryo(), cachePath, SolutionPath.class, storeMode, new Store.Loader<SolutionPath>() {
			@Override
			public SolutionPath load() {
				SolutionPath solution = new SolutionPath(grade);
				try {
					CSVParser parser = new CSVParser(new FileReader(logFile), CSVFormat.EXCEL.withHeader());
					
					DateFormat format = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
					
					String lastGrab = null;
					
					String gradedID = grade == null ? null : grade.gradedID;
					boolean foundGraded = false;
					
					BlockIndex editingIndex = null;
					Snapshot lastSnaphot = null;
					
					List<DataRow> currentWork = new ArrayList<DataRow>();
					
					for (CSVRecord record : parser) {
						String timestampString = record.get(1);
						Date timestamp = null;
						try {
							timestamp = format.parse(timestampString);
						} catch (ParseException e) {
							e.printStackTrace();
						}
						
						if (timestamp != null && (
								(minDate != null && timestamp.before(minDate)) || 
								(maxDate != null && timestamp.after(maxDate)))) {
							continue;
						}
						
						String action = record.get(2);
						String data = record.get(3);
						
						String xml = record.get(8);
						if (snapshotsOnly && action.equals("Block.grabbed")) {
							if (xml.length() > 2) lastGrab = xml;
							continue;
						} else if (xml.length() <= 2 && lastGrab != null) {
							xml = lastGrab;
						}
						
						String idS = record.get(0);
						int id = -1;
						try {
							id = Integer.parseInt(idS);
						} catch (NumberFormatException e) { }
						
						DataRow row = new DataRow(id, timestamp, action, data, xml);
						if (row.snapshot != null) lastSnaphot = row.snapshot;
						
						if ("BlockEditor.start".equals(action)) {
							JSONObject json = new JSONObject(data);
							String name = json.getString("spec");
							String type = json.getString("type");
							String category = json.getString("category");
							editingIndex = lastSnaphot.getEditingIndex(name, type, category);
							if (editingIndex == null) {
								System.err.println("Edit index not found");
							}
						} else if ("BlockEditor.ok".equals(action)) {
							editingIndex = null;
						}
						if (row.snapshot != null) {
							if (row.snapshot.editing == null) editingIndex = null;
							else if (row.snapshot.editing.guid == null) {
								row.snapshot.setEditingIndex(editingIndex);
							}
						}
						
						if (!snapshotsOnly || row.snapshot != null) {
							currentWork.add(row);
							lastGrab = null;
						}
						
						if (idS.equals(gradedID)) {
							foundGraded = true;
						}
						
						if (action.equals("IDE.exportProject")) {
							solution.exported = true;
							for (DataRow r : currentWork) {
								solution.add(r);
							}
							currentWork.clear();
							if (foundGraded) break;
						}
						
					}
					parser.close();
					
					if (gradedID != null && !foundGraded) {
						System.err.println("No grade row for: " + logFile.getName());
					}
					
					System.out.println("Parsed: " + logFile.getName());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				Collections.sort(solution.rows);
				
				return solution;
			}
		});
	}
	
//	private class Labeling {
//		public final Snapshot snapshot;
//		
//		public Labeling(Snapshot snapshot) {
//			this.snapshot = snapshot;
//		}
//		
//		public void load(Labeling last) {
//			
//		}
//	}
//	
//	private class Label {
//		public Block block;
//		public Label parent;
//		public List<Block> children = new LinkedList<Block>();
//		public int id;
//		
//		
//	}
	
	
	public Map<String, SolutionPath> parseAssignment(String folder, final boolean snapshotsOnly) {
		return parseAssignment(folder, snapshotsOnly, null, null);
	}
	
	public Map<String, SolutionPath> parseAssignment(String folder, final boolean snapshotsOnly,
			final Date minDate, final Date maxDate) {
		HashMap<String, Grade> grades = parseGrades(folder);
		
		final Map<String, SolutionPath> students = new TreeMap<String, SolutionPath>();
		final AtomicInteger threads = new AtomicInteger();
		for (File file : new File(outputFolder + "/" + "parsed", folder).listFiles()) {
			if (!file.getName().endsWith(".csv")) continue;
			final File fFile = file;
			final Grade grade = grades.get(file.getName().replace(".csv", ""));
			threads.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						SolutionPath rows = parseRows(fFile, grade, snapshotsOnly, minDate, maxDate);
						if (rows.grade == null || !rows.grade.outlier) {
							if (rows.size() > 3) {
								synchronized (students) {
									students.put(fFile.getName(), rows);
								}
							}							
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					threads.decrementAndGet();
				}
			}).run(); // TODO: Figure out why parallel doesn't work
		}
		while (threads.get() != 0) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return students;
	}
	
	
	private HashMap<String, Grade> parseGrades(String folder) {
		HashMap<String, Grade> grades = new HashMap<String, Grade>();
		
		File file = new File(outputFolder, "grades/" + folder + ".csv");
		if (!file.exists()) return grades;
		
		try {
			CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withHeader());
			for (CSVRecord record : parser) {
				Grade grade = new Grade(record);
				grades.put(grade.id, grade);
			}
			parser.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return grades;
	}
	
	public static void clean(String path) {
		for (String file : new File(path).list()) {
			File f = new File(path, file);
			if (f.isDirectory()) clean(f.getAbsolutePath());
			else if (f.getName().endsWith(".cached")) f.delete();
		}
	}

	public static void main(String[] args) throws IOException {
//		SnapParser.splitStudentRecords("../data/csc200/fall2015.csv");
//		clean("../data/csc200/spring2016/parsed");

		Assignment.Spring2016.PolygonMaker.load(Mode.Overwrite, true);
		
	}
}




