package edu.isnap.hint.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class Spreadsheet {
	private List<Map<String, Object>> rows = new LinkedList<>();
	private Map<String,Object> row;

	public void newRow() {
		row = new LinkedHashMap<>();
		rows.add(row);
	}

	public void put(String key, Object value) {
		row.put(key, value);
	}

	public void put(String key, boolean value) {
		put(key, value ? "TRUE" : "FALSE");
	}

	public void write(String path) throws FileNotFoundException, IOException {
		if (rows.size() == 0) return;
		String[] header = row.keySet().toArray(new String[row.keySet().size()]);
		File file = new File(path);
		file.getParentFile().mkdirs();
		CSVPrinter printer = new CSVPrinter(new PrintStream(file),
				CSVFormat.DEFAULT.withHeader(header));

		for (Map<String,Object> row : rows) {
			printer.printRecord(row.values());
		}

		printer.close();
	}

	public void sort(Comparator<Map<String, Object>> comparator) {
		rows.sort(comparator);
	}
}