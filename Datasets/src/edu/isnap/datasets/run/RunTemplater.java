package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.ctd.graph.Node;
import edu.isnap.datasets.BJCSolutions2017;
import edu.isnap.template.parse.TemplateParser;

public class RunTemplater {
	public static void main(String[] args) throws IOException {
		Node.PrettyPrintSpacing = 4;
		TemplateParser.parseTemplate(BJCSolutions2017.U5_L1_Search);
//		for (Assignment assignment : BJCSolutions2017.All) {
//			TemplateParser.parseTemplate(assignment);
//		}
	}
}
