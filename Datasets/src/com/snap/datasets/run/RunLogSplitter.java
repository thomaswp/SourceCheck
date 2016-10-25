package com.snap.datasets.run;

import java.io.IOException;

import com.snap.parser.HelpSeeking;
import com.snap.parser.LogSplitter;

public class RunLogSplitter {
	public static void main(String[] args) throws IOException {
		// Replace "Fall2015" with the dataset you want to load
		LogSplitter.splitStudentRecords(HelpSeeking.dataFile);
	}
}
