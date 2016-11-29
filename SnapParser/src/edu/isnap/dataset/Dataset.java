package edu.isnap.dataset;

import java.util.Date;

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

}