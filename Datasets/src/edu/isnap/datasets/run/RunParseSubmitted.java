package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.HelpSeeking;
import edu.isnap.parser.ParseSubmitted;

public class RunParseSubmitted {
	public static void main(String[] args) throws IOException {
//		for (Assignment assignment : Fall2016.All) {
//			ParseSubmitted.parseSubmitted(assignment);
//		}
		ParseSubmitted.parseSubmitted(HelpSeeking.BrickWall);
//		ParseSubmitted.printToGrade(Fall2015.GuessingGame1);
	}
}
