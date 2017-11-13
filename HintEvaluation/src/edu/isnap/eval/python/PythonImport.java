package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.json.JSONException;

import edu.isnap.ctd.graph.ASTNode;

public class PythonImport {

	public static void main(String[] args) throws IOException {
		loadAssignment("../../PythonAST/data", "canDrinkAlcohol");
	}

	static void loadAssignment(String dataDir, String assignment) throws IOException {
		for (File file : new File(dataDir, assignment).listFiles()) {
			if (!file.getName().endsWith(".json")) continue;
			ASTNode node;
			try {
				String source = new String(Files.readAllBytes(file.toPath()));
				node = ASTNode.parse(source);
			} catch (JSONException e) {
				System.out.println("Error parsing: " + file.getAbsolutePath());
				e.printStackTrace();
				break;
			}
			System.out.println(file.getName());
			System.out.println(node.toNode().prettyPrint(true));
		}
	}
}
