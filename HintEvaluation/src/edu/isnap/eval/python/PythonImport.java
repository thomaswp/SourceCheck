package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.NullSream;
import edu.isnap.ctd.util.map.ListMap;

public class PythonImport {

	public static void main(String[] args) throws IOException {
		generateHints("../../PythonAST/data", "howManyEggCartons");

//		GrammarBuilder builder = new GrammarBuilder("python", new HashMap<>());
//		ListMap<String,PythonNode> nodes = loadAllAssignments("../../PythonAST/data");
//		for (String assignment : nodes.keySet()) {
//			List<PythonNode> correct = nodes.get(assignment).stream()
//					.filter(n -> n.correct.orElse(false))
//					.collect(Collectors.toList());
//			if (correct.size() > 0) {
//				System.out.println(assignment + ": " + correct.size() + "/" +
//					nodes.get(assignment).size());
//			}
//		}
//		nodes.values().forEach(list -> list.forEach(n -> builder.add(n)));
//		System.out.println(builder.toJSON());
	}

	static void generateHints(String dataDir, String assignment) throws IOException {
		ListMap<String, PythonNode> attempts = loadAssignment(dataDir, assignment);
		List<PythonNode> correct = attempts.values().stream()
			.map(list -> list.get(list.size() - 1))
			.filter(n -> n.correct.orElse(false))
			.collect(Collectors.toList());
		for (String student : attempts.keySet()) {
			PythonNode firstAttempt = attempts.get(student).get(0);
			if (firstAttempt.correct.orElse(false)) continue;
			List<Node> subset = correct.stream()
					.filter(n -> !student.equals(n.student))
					.collect(Collectors.toList());
			HintHighlighter highlighter = new HintHighlighter(subset, new PythonConfig());
			highlighter.trace = NullSream.instance;

			String from = firstAttempt.prettyPrint(true);
			List<EditHint> edits = highlighter.highlight(firstAttempt);
			Node copy = firstAttempt.copy();
			EditHint.applyEdits(copy, edits);
			String to = copy.prettyPrint(true);

			System.out.println(student);
			System.out.println(firstAttempt.source);
			System.out.println(Diff.diff(from, to));
			System.out.println(String.join("\n",
					edits.stream().map(e -> e.toString()).collect(Collectors.toList())));
			System.out.println("------------------------");
			System.out.println();

		}

	}

	static Map<String, ListMap<String, PythonNode>> loadAllAssignments(String dataDir)
			throws IOException {
		Map<String, ListMap<String, PythonNode>> map = new LinkedHashMap<>();
		for (File dir : new File(dataDir).listFiles(f -> f.isDirectory())) {
			String assignment = dir.getName();
			map.put(assignment, loadAssignment(dataDir, assignment));
		}
		return map;
	}

	static ListMap<String, PythonNode> loadAssignment(String dataDir, String assignment)
			throws IOException {
		ListMap<String, PythonNode> nodes = new ListMap<>();
		for (File studentDir : new File(dataDir, assignment).listFiles(File::isDirectory)) {
			// TODO: maybe don't use the folder name?
			String student = studentDir.getName();
			for (File file : studentDir.listFiles()) {
				if (!file.getName().endsWith(".json")) continue;
				PythonNode node;
				try {
					String json = new String(Files.readAllBytes(file.toPath()));
					JSONObject obj = new JSONObject(json);
					ASTNode astNode = ASTNode.parse(obj);
					astNode.autoID("");
					node = (PythonNode) astNode.toNode(PythonNode::new);
					if (obj.has("correct")) {
						boolean correct = obj.getBoolean("correct");
						node.correct = Optional.of(correct);
						node.student = student;
					}
					File sourceFile = new File(studentDir, file.getName().replace(".json", ".py"));
					if (sourceFile.exists()) {
						String source = new String(Files.readAllBytes(sourceFile.toPath()));
						node.source = source;
					}
				} catch (JSONException e) {
					System.out.println("Error parsing: " + file.getAbsolutePath());
					e.printStackTrace();
					break;
				}
//				System.out.println(file.getName());
//				System.out.println(node.toNode(PythonNode::new).prettyPrint(true));
				nodes.add(student, node);
			}
		}
		return nodes;
	}

	static class PythonNode extends Node {

		public Optional<Boolean> correct = Optional.empty();
		public String student;
		public String source;

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
