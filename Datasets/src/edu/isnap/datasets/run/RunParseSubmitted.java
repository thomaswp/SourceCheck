package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.Fall2018;
import edu.isnap.parser.ParseSubmitted;

public class RunParseSubmitted {
	public static void main(String[] args) throws IOException {
		// NOTE: You may need to use the -Xmx argument to increase max memory for some runs

//		for (Assignment assignment : Spring2017.All) {
//			ParseSubmitted.parseSubmitted(assignment);
//		}
		ParseSubmitted.parseSubmitted(Fall2018.GuessingGame2);
//		ParseSubmitted.printToGrade(Fall2015.GuessingGame1);
	}
}
