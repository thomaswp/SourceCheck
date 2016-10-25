package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Fall2016;
import edu.isnap.parser.ParseSubmitted;

public class RunParseSubmitted {
	public static void main(String[] args) throws IOException {
		for (Assignment assignment : Fall2016.All) {
			ParseSubmitted.parseSubmitted(assignment);
		}
//		ParseSubmitted.parseSubmitted(Fall2016.GuessingGame1);
//		ParseSubmitted.printToGrade(Fall2015.GuessingGame1);
	}
}
