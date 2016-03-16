package com.snap.eval.user;

import java.io.IOException;

import com.snap.parser.Store.Mode;

public class SetupData {
	
	// Run this once to parse through the data
	public static void main(String[] args) throws IOException {
		// DON'T interrupt this line while running - it may freeze
//		parser.splitStudentRecords();
		for (Assignment assignment : Assignment.Spring2016.All) {
			assignment.load(Mode.Overwrite, false);
		}
	}
}
