package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.map.ListMap;

public class PythonImport {

	public static void main(String[] args) throws IOException {
//		List<PythonNode> nodes = loadAssignment("../../PythonAST/data", "convertToDegrees");
//		nodes.stream()
//		.filter(n -> n.correct.orElse(false))
//		.forEach(n -> System.out.println(n.prettyPrint(true)));
//		GrammarBuilder builder = new GrammarBuilder("python", new HashMap<>());
		ListMap<String,PythonNode> nodes = loadAllAssignments("../../PythonAST/data");
		for (String assignment : nodes.keySet()) {
			List<PythonNode> correct = nodes.get(assignment).stream()
					.filter(n -> n.correct.orElse(false))
					.collect(Collectors.toList());
			if (correct.size() > 0) {
				System.out.println(assignment + ": " + correct.size() + "/" + nodes.get(assignment).size());
			}
		}
//		nodes.values().forEach(list -> list.forEach(n -> builder.add(n)));
//		System.out.println(builder.toJSON());
	}

	static ListMap<String, PythonNode> loadAllAssignments(String dataDir) throws IOException {
		ListMap<String, PythonNode> map = new ListMap<>();
		for (File dir : new File(dataDir).listFiles(f -> f.isDirectory())) {
			String assignment = dir.getName();
			map.put(assignment, loadAssignment(dataDir, assignment));
		}
		return map;
	}

	static List<PythonNode> loadAssignment(String dataDir, String assignment) throws IOException {
		List<PythonNode> nodes = new ArrayList<>();
		for (File file : new File(dataDir, assignment).listFiles()) {
			if (!file.getName().endsWith(".json")) continue;
			PythonNode node;
			try {
				String source = new String(Files.readAllBytes(file.toPath()));
				JSONObject obj = new JSONObject(source);
				node = (PythonNode) ASTNode.parse(obj).toNode(PythonNode::new);
				if (obj.has("correct")) {
					boolean correct = obj.getBoolean("correct");
					node.correct = Optional.of(correct);
				}
			} catch (JSONException e) {
				System.out.println("Error parsing: " + file.getAbsolutePath());
				e.printStackTrace();
				break;
			}
//			System.out.println(file.getName());
//			System.out.println(node.toNode(PythonNode::new).prettyPrint(true));
			nodes.add(node);
		}
		return nodes;
	}

	static class PythonNode extends Node {

		public Optional<Boolean> correct = Optional.empty();

		protected PythonNode(Node parent, String type, String value, String id) {
			super(parent, type, value, id);
		}

		@Override
		public Node constructNode(Node parent, String type, String value, String id) {
			return new PythonNode(parent, type, value, id);
		}

		@Override
		protected boolean nodeTypeHasBody(String type) {
			return "list".equals(type);
		}

	}
}
