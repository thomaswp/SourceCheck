package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.CSC200Solutions;
import edu.isnap.template.parse.TemplateParser;

public class RunTemplater {
	public static void main(String[] args) throws IOException {
		Node.PrettyPrintSpacing = 4;
		Node.PrettyPrintUseColon = true;
//		TemplateParser.parseSnapTemplate(CSC200Solutions.Pong1Lab);
		for (Assignment assignment : CSC200Solutions.All) {
			TemplateParser.parseSnapTemplate(assignment);
		}
	}
}