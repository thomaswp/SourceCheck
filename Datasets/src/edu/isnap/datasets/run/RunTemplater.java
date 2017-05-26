package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.ctd.graph.Node;
import edu.isnap.datasets.BJCSolutions2017;
import edu.isnap.template.parse.Parser;

public class RunTemplater {
	public static void main(String[] args) throws IOException {
		Node.PrettyPrintSpacing = 4;
		Parser.parseTemplate(BJCSolutions2017.U2_L2_Predicates);
//		for (Assignment assignment : BJCSolutions2017.All) {
//			Parser.parseTemplate(assignment);
//		}
	}
}
