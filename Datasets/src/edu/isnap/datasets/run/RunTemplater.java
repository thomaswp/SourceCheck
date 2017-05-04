package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.BJCSolutions2017;
import edu.isnap.template.parse.Parser;

public class RunTemplater {
	public static void main(String[] args) throws IOException {
		Parser.parseTemplate(BJCSolutions2017.U1_L1_Alonzo);
//		Parser.parseTemplate(BJCSolutions2017.U1_P1_LineArt);
	}
}
