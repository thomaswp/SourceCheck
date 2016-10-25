package com.snap.eval.user;

import java.io.IOException;

import com.snap.parser.Assignment;
import com.snap.parser.LogSplitter;
import com.snap.parser.Spring2016;
import com.snap.parser.Store.Mode;

public class SetupData {

	// Run this once to parse through the data (and then don't run it again!)
	public static void main(String[] args) throws IOException {
		// DON'T interrupt this line while running - it may freeze
		LogSplitter.splitStudentRecords(Spring2016.dataFile);
		// Go ahead and pre-load (cache) the assignments we'll be working with
		for (Assignment assignment : Spring2016.All) {
			System.out.println("Loading: " + assignment.name);
			assignment.load(Mode.Overwrite, false);
		}
	}
}
