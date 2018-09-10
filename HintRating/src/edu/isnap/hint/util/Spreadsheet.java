package edu.isnap.hint.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class Spreadsheet {
	public final static String TRUE = "TRUE", FALSE = "FALSE";

	private List<Map<String, Object>> rows = new LinkedList<>();
	private Map<String, Object> header = new LinkedHashMap<>();
	private Map<String,Object> row;
	private CSVPrinter printer;
	private PrintStream printStream;

	public boolean isWriting() {
		return printStream != null;
	}

	public void newRow() {
		try {
			writeRows();
		} catch (IOException e) {
			e.printStackTrace();
		}
		row = new LinkedHashMap<>();
		for (String key : header.keySet()) {
			row.put(key, header.get(key));
		}
		rows.add(row);
	}

	public void put(String key, Object value) {
		if (row == null) throw new RuntimeException("Must create a newRow() before calling put()");
		row.put(key, value);
	}

	public void put(String key, boolean value) {
		put(key, value ? TRUE : FALSE);
	}

	public void beginWrite(String path) throws IOException {
		File file = new File(path);
		File parent = file.getParentFile();
		if (parent != null) parent.mkdirs();
		beginWrite(new FileOutputStream(file));
	}

	public void beginWrite(OutputStream printStream) throws IOException {
		if (isWriting()) throw new RuntimeException("Already writing");
		this.printStream = new PrintStream(printStream);
		writeRows();
	}

	private void writeRows() throws IOException {
		if (!isWriting()) return;
		if (row == null) return;
		if (printer == null) {
			String[] header = row.keySet().toArray(new String[row.keySet().size()]);
			printer = new CSVPrinter(printStream, CSVFormat.DEFAULT.withHeader(header));
		}
		for (Map<String,Object> row : rows) {
			printer.printRecord(row.values());
		}
		rows.clear();
	}

	public void endWrite() throws IOException {
		if (!isWriting()) return;
		writeRows();
		printer.close();
		printer = null;
		printStream = null;
	}

	public void write(String path) throws FileNotFoundException, IOException {
		if (rows.size() == 0) return;
		beginWrite(path);
		endWrite();
	}

	public void write(OutputStream stream) throws FileNotFoundException, IOException {
		if (rows.size() == 0) return;
		beginWrite(stream);
		endWrite();
	}

	public void sort(Comparator<Map<String, Object>> comparator) {
		rows.sort(comparator);
	}

	public void setHeader(String key, Object value) {
		header.put(key, value);
	}
}