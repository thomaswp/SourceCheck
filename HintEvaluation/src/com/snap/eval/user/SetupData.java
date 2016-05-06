package com.snap.eval.user;

import java.io.IOException;

import com.snap.parser.Assignment;
import com.snap.parser.SnapParser;
import com.snap.parser.Store.Mode;

public class SetupData {
	
	// Run this once to parse through the data (and then don't run it again!)
	public static void main(String[] args) throws IOException {
		// DON'T interrupt this line while running - it may freeze
		SnapParser.splitStudentRecords("../data/csc200/spring2016.csv");
		// Go ahead and pre-load (cache) the assignments we'll be working with
		for (Assignment assignment : Assignment.Spring2016.All) {
			System.out.println("Loading: " + assignment.name);
			assignment.load(Mode.Overwrite, false);
		}
	}
}
