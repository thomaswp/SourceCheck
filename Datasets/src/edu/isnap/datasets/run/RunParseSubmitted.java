package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.Fall2015;
import edu.isnap.parser.ParseSubmitted;

public class RunParseSubmitted {
	public static void main(String[] args) throws IOException {
//		for (Assignment assignment : Fall2016.All) {
//			ParseSubmitted.parseSubmitted(assignment);
//		}
		ParseSubmitted.parseSubmitted(Fall2015.Squiral);
//		ParseSubmitted.printToGrade(Fall2015.GuessingGame1);
	}
}
