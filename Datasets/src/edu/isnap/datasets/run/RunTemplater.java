package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.ctd.graph.Node;
import edu.isnap.datasets.CSC200Solutions;
import edu.isnap.template.parse.TemplateParser;

public class RunTemplater {
	public static void main(String[] args) throws IOException {
		Node.PrettyPrintSpacing = 4;
		TemplateParser.parseTemplate(CSC200Solutions.GuessingGame2New);
//		for (Assignment assignment : CSC200Solutions.All) {
//			TemplateParser.parseTemplate(assignment);
//		}
	}
}
