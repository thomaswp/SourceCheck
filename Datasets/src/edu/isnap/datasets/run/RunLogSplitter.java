package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.HelpSeeking;
import edu.isnap.parser.LogSplitter;
import edu.isnap.parser.SnapParser;

public class RunLogSplitter {
	public static void main(String[] args) throws IOException {
		// Replace with the dataset you want to load
		Dataset dataset = HelpSeeking.instance;

		// Note: Prior to running this you must *manually* delete the "parsed" directory of the
		// dataset folder
		SnapParser.clean(dataset.dataDir);
		LogSplitter.splitStudentRecords(dataset.dataFile);
	}
}
