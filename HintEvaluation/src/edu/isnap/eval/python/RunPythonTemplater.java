package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.python.PythonImport.PythonNode;
import edu.isnap.template.parse.TemplateParser;

public class RunPythonTemplater {
	public static void main(String[] args) throws IOException {
		parse("../data/itap/templates/helloWorld");
	}

	private static void parse(String baseFile) throws IOException {
		String source = new String(Files.readAllBytes(new File(baseFile + ".json").toPath()));
		Node sample = JsonAST.toNode(ASTNode.parse(source), PythonNode::new);
		TemplateParser.parseTemplate(baseFile, sample, new PythonHintConfig());
	}
}
