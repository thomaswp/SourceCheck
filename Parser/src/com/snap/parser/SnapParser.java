package com.snap.parser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.esotericsoftware.kryo.Kryo;
import com.snap.parser.Store.Mode;

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
	
	public SolutionPath parseRows(final File logFile) throws IOException {
		String cachePath = logFile.getAbsolutePath() + ".cached";
		
		return Store.getCachedObject(new Kryo(), cachePath, SolutionPath.class, storeMode, new Store.Loader<SolutionPath>() {
			@Override
			public SolutionPath load() {
				SolutionPath solution = new SolutionPath();
				try {
					CSVParser parser = new CSVParser(new FileReader(logFile), CSVFormat.EXCEL.withHeader());
					
					DateFormat format = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
					
					String lastGrab = null;
					
					for (CSVRecord record : parser) {
						String timestampString = record.get(1);
						Date timestamp = null;
						try {
							timestamp = format.parse(timestampString);
						} catch (ParseException e) {
							e.printStackTrace();
						}
						
						String action = record.get(2);
						if (action.equals("IDE.exportProject")) {
							solution.exported = true;
						}
						
						String xml = record.get(8);
						if (action.equals("Block.grabbed")) {
							if (xml.length() > 2) lastGrab = xml;
							continue;
						} else if (xml.length() <= 2 && lastGrab != null) {
							xml = lastGrab;
						}
						DataRow row = new DataRow(timestamp, action, xml);
						
						if (row.snapshot != null) {
							solution.add(row);
							lastGrab = null;
						}
						
					}
					parser.close();
					System.out.println("Parsed: " + logFile.getName());
				} catch (Exception e) {
					e.printStackTrace();
				}
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
	
	
	public HashMap<String, SolutionPath> parseAssignment(String folder) {
		final HashMap<String, SolutionPath> students = new HashMap<String, SolutionPath>();
		final AtomicInteger threads = new AtomicInteger();
		for (File file : new File(outputFolder, folder).listFiles()) {
			if (!file.getName().endsWith(".csv")) continue;
			final File fFile = file;
			threads.incrementAndGet();
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						SolutionPath rows = parseRows(fFile);
						if (rows.size() > 3) {
							students.put(fFile.getName(), rows);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					threads.decrementAndGet();
				}
			}).start();
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
	
	
	public static void main(String[] args) throws IOException {
		SnapParser parser = new SnapParser("../data/csc200/fall2015", Mode.Overwrite);
//		parser.splitStudentRecords("../data/csc200/fall2015.csv");
		parser.parseAssignment("guess1Lab");
		parser.parseAssignment("guess2HW");
		parser.parseAssignment("guess3Lab");
	}
}




