package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.HelpSeeking;
import edu.isnap.parser.LogSplitter;

public class RunLogSplitter {
	public static void main(String[] args) throws IOException {
		// Replace "Fall2015" with the dataset you want to load
		LogSplitter.splitStudentRecords(HelpSeeking.dataFile);
	}
}
