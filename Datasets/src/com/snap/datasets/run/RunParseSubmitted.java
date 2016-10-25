package com.snap.datasets.run;

import java.io.IOException;

import com.snap.parser.Assignment;
import com.snap.parser.Fall2016;
import com.snap.parser.ParseSubmitted;

public class RunParseSubmitted {
	public static void main(String[] args) throws IOException {
		for (Assignment assignment : Fall2016.All) {
			ParseSubmitted.parseSubmitted(assignment);
		}
//		ParseSubmitted.parseSubmitted(Fall2016.GuessingGame1);
//		ParseSubmitted.printToGrade(Fall2015.GuessingGame1);
	}
}
