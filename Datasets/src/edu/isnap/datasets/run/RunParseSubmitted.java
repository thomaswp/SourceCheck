package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.csc110.CSC110Fall2019;
import edu.isnap.parser.ParseSubmitted;

public class RunParseSubmitted {
	public static void main(String[] args) throws IOException {
		// NOTE: You may need to use the -Xmx argument to increase max memory for some runs

//		for (Assignment assignment : Spring2017.All) {
//			ParseSubmitted.parseSubmitted(assignment);
//		}
		ParseSubmitted.parseSubmitted(CSC110Fall2019.DaisyDesign);
//		ParseSubmitted.printToGrade(Fall2015.GuessingGame1);
	}
}
