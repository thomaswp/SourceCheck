package edu.isnap.rating;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.hint.util.Spreadsheet;

public abstract class TraceDataset {

	protected final ListMap<String, Trace> traceMap = new ListMap<>();

	public final String name;

	public TraceDataset(String name) {
		this.name = name;
	}

	public Collection<String> getAssignmentIDs() {
		return traceMap.keySet();
	}

	public List<Trace> getTraces(String assignmentID) {
		return Collections.unmodifiableList(traceMap.get(assignmentID));
	}

	private void sort() {
		traceMap.values().forEach(Collections::sort);
	}

	@Deprecated
	protected void addDirectory(String directory) throws IOException{
		File dirFile = new File(directory);
		if (!dirFile.exists()) {
			throw new FileNotFoundException("Directory does not exist: " +
					dirFile.getAbsolutePath());
		}
		for (File assignmentDir : dirFile.listFiles(f -> f.isDirectory())) {
			addAssignment(assignmentDir);
		}
		sort();
	}

	private void addAssignment(File directory) throws IOException {
		String assignmentID = directory.getName();
		for (File attemptDir : directory.listFiles(f -> f.isDirectory())) {
			Trace trace = new Trace(attemptDir.getName(), assignmentID);
			for (File snapshotFile : attemptDir.listFiles()) {
				if (!snapshotFile.getName().toLowerCase().endsWith(".json")) {
					System.err.println("Unknown file: " + snapshotFile.getAbsolutePath());
					continue;
				}
				String source = new String(Files.readAllBytes(snapshotFile.toPath()));
				ASTNode node = ASTNode.parse(source);
				trace.add(node);
			}
			traceMap.add(assignmentID, trace);
		}
	}

	protected void addSpreadsheet(String path) throws IOException {
		String lcPath = path.toLowerCase();
		boolean zip = lcPath.endsWith(".gz") || lcPath.endsWith(".gzip");
		InputStream in = new FileInputStream(path);
		if (zip) in = new GZIPInputStream(in);
		CSVParser parser = new CSVParser(new InputStreamReader(in), CSVFormat.DEFAULT.withHeader());
		Trace trace = null;
		for (CSVRecord record : parser) {
			String index = record.get("index");
			if ("0".equals(index)) {
				String assignmentID = record.get("assignmentID");
				String traceID = record.get("traceID");
				trace = new Trace(traceID, assignmentID);
				traceMap.add(assignmentID, trace);
			}

			String json = record.get("code");
			ASTNode node = ASTNode.parse(json);
			trace.add(node);
		}
		parser.close();
		sort();
	}

	public void writeToSpreadsheet(String path, boolean zip) throws IOException {
		String lcPath = path.toLowerCase();
		if (zip && !lcPath.endsWith(".gz") && !lcPath.endsWith(".gzip")) path += ".gz";
		OutputStream out = new FileOutputStream(path);
		if (zip) out = new GZIPOutputStream(out);
		Spreadsheet spreadsheet = new Spreadsheet();
		spreadsheet.beginWrite(out);
		for (String assignmentID : traceMap.keySet()) {
			for (Trace trace : traceMap.get(assignmentID)) {
				int i = 0;
				for (ASTNode node : trace) {
					spreadsheet.newRow();
					spreadsheet.put("assignmentID", trace.assignmentID);
					spreadsheet.put("traceID", trace.id);
					spreadsheet.put("index", i++);
					spreadsheet.put("code", node.toJSON().toString());
				}
			}
		}
		spreadsheet.endWrite();

		out.close();
	}
}
