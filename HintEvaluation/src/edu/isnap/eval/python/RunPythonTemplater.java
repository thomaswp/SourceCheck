package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import edu.isnap.ctd.hint.HintMap;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.python.PythonImport.PythonNode;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.template.parse.TemplateParser;

public class RunPythonTemplater {
	public static void main(String[] args) throws IOException {
		Node.PrettyPrintSpacing = 4;
		Node.PrettyPrintUseColon = true;
		String[] problems = new String[] {
				"helloWorld",
				"firstAndLast",
				"isPunctuation",
				"kthDigit",
				"oneToN",
		};
		for (String problem : problems) {
			parse("../data/itap/single/templates", problem);
			parse("../data/itap/templates", problem);
		}
	}

	private static void parse(String baseDir, String assignment) throws IOException {
		String source = new String(Files.readAllBytes(
				new File(baseDir, assignment + ".json").toPath()));
		Node sample = JsonAST.toNode(ASTNode.parse(source), PythonNode::new);
		String templatePath = new File(baseDir, assignment).getPath();
		HintMap hintMap = TemplateParser.parseTemplate(
				templatePath, sample, new PythonHintConfig());
		TemplateParser.saveHintMap(hintMap, baseDir, assignment);
	}
}
