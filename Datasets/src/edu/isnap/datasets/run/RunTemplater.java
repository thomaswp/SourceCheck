package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.ctd.graph.Node;
import edu.isnap.datasets.CSC200Solutions;
import edu.isnap.template.parse.TemplateParser;

public class RunTemplater {
	public static void main(String[] args) throws IOException {
		Node.PrettyPrintSpacing = 4;
		TemplateParser.parseTemplate(CSC200Solutions.Squiral);
//		for (Assignment assignment : BJCSolutions2017.All) {
//			TemplateParser.parseTemplate(assignment);
//		}
	}
}
