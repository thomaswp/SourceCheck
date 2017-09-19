package edu.isnap.dataset;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;

public abstract class Dataset {
	public final Date start;
	public final String dataDir, dataFile;

	public abstract Assignment[] all();

	public Dataset(Date start, String dataDir) {
		this.start = start;
		this.dataDir = dataDir;
		this.dataFile = dataDir + ".csv";
	}

	public String getName() {
		return getClass().getSimpleName();
	}

	public String analysisDir() {
		return dataDir + "/analysis";
	}

	public String startsFile() {
		return dataDir + "/starts.csv";
	}

	public CSVFormat dataFileCSVFormat() {
		return CSVFormat.DEFAULT.withHeader();
	}

	public Map<String, Assignment> getAssignmentMap() {
		Map<String, Assignment> map = new HashMap<>();
		for (Assignment assignment : all()) {
			map.put(assignment.name, assignment);
		}
		return map;
	}
}