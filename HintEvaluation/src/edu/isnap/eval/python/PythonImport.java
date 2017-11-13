package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONException;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.eval.export.GrammarBuilder;

public class PythonImport {

	public static void main(String[] args) throws IOException {
		GrammarBuilder builder = new GrammarBuilder("python", new HashMap<>());
//		List<ASTNode> nodes = loadAssignment("../../PythonAST/data", "canDrinkAlcohol");
		ListMap<String, ASTNode> nodes = loadAllAssignments("../../PythonAST/data");
		nodes.values().forEach(list -> list.forEach(n -> builder.add(n)));
		System.out.println(builder.toJSON());
	}

	static ListMap<String, ASTNode> loadAllAssignments(String dataDir) throws IOException {
		ListMap<String, ASTNode> map = new ListMap<>();
		for (File dir : new File(dataDir).listFiles(f -> f.isDirectory())) {
			String assignment = dir.getName();
			map.put(assignment, loadAssignment(dataDir, assignment));
		}
		return map;
	}

	static List<ASTNode> loadAssignment(String dataDir, String assignment) throws IOException {
		List<ASTNode> nodes = new ArrayList<>();
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
//			System.out.println(file.getName());
//			System.out.println(node.toNode().prettyPrint(true));
			nodes.add(node);
		}
		return nodes;
	}
}
