package edu.isnap.eval.python;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.edit.Deletion;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.hint.edit.Insertion;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.ctd.util.NullStream;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.node.ASTNode;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.util.Diff;
import edu.isnap.util.map.ListMap;

public class PythonImport {

	public static void main(String[] args) throws IOException {
//		generateHints("../../PythonAST/data/datacamp", "65692");
		generateHints("../../PythonAST/data/itap", "firstAndLast");

//		Map<String, ListMap<String, PythonNode>> nodes = loadAllAssignments("../../PythonAST/data");
//		for (String assignment : nodes.keySet()) {
//			long correct = nodes.get(assignment).values().stream()
//					.filter(list -> list.stream().anyMatch(n -> n.correct.orElse(false)))
//					.count();
//			if (correct > 0) {
//				System.out.println(assignment + ": " + correct + "/" +
//					nodes.get(assignment).size());
//			}
//		}
//		GrammarBuilder builder = new GrammarBuilder("python", new HashMap<>());
//		nodes.values().forEach(listMap -> listMap.values()
//				.forEach(list -> list.forEach(n -> builder.add(n))));
//		System.out.println(builder.toJSON());
	}

	static void generateHints(String dataDir, String assignment) throws IOException {
		ListMap<String, PythonNode> attempts = loadAssignment(dataDir, assignment);
		Set<PythonNode> correct = attempts.values().stream()
			.map(list -> list.get(list.size() - 1))
			.filter(n -> n.correct.orElse(false))
			.collect(Collectors.toSet());
		for (String student : attempts.keySet()) {
			PythonNode firstAttempt = attempts.get(student).get(0);
			if (firstAttempt.correct.orElse(false)) continue;
			Set<Node> subset = correct.stream()
					.filter(n -> !student.equals(n.student))
					.collect(Collectors.toSet());
			HintHighlighter highlighter = new HintHighlighter(subset, new PythonHintConfig());
			highlighter.trace = NullStream.instance;

			String from = firstAttempt.prettyPrint(true);
			List<EditHint> edits = highlighter.highlight(firstAttempt);
			Node copy = firstAttempt.copy();
			EditHint.applyEdits(copy, edits);
			String to = copy.prettyPrint(true);

			Mapping mapping = highlighter.findSolutionMapping(firstAttempt);
			PythonNode target = (PythonNode) mapping.to;

			System.out.println(student);
			System.out.println(firstAttempt.source);
			System.out.println("\nTarget (" + target.student + "):");
			System.out.println(Diff.diff(firstAttempt.source, target.source, 2));
			mapping.printValueMappings(System.out);
			System.out.println();

			for (EditHint hint : edits) {
				ASTNode toDelete = null;
				if (hint instanceof Deletion) {
					Deletion del = (Deletion) hint;
					toDelete = (ASTNode) del.node.tag;
				}
				if (hint instanceof Insertion) {
					Insertion ins = (Insertion) hint;
					if (ins.replaced != null) toDelete = (ASTNode) ins.replaced.tag;
				}
				if (toDelete == null || toDelete.hasType("null")) continue;
				System.out.println(hint);
				System.out.println(toDelete);
				System.out.println(toDelete.getSourceLocation());
				System.out.println(toDelete.getSourceLocation().markSource(firstAttempt.source));
			}

//			System.out.println(Diff.diff(from, to));
//			System.out.println(String.join("\n",
//					edits.stream().map(e -> e.toString()).collect(Collectors.toList())));
//			System.out.println("------------------------");
//			System.out.println();

		}

	}

	public static Map<String, ListMap<String, PythonNode>> loadAllAssignments(String dataDir)
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
					String source = null;
					File sourceFile = new File(studentDir, file.getName().replace(".json", ".py"));
					if (sourceFile.exists()) {
						source = new String(Files.readAllBytes(sourceFile.toPath()));
					}
					String json = new String(Files.readAllBytes(file.toPath()));
					JSONObject obj = new JSONObject(json);
					ASTSnapshot astNode = ASTSnapshot.parse(obj, source);
					node = (PythonNode) JsonAST.toNode(astNode, PythonNode::new);
					if (obj.has("correct")) {
						boolean correct = obj.getBoolean("correct");
						node.correct = Optional.of(correct);
						node.student = student;
					}
					node.source = source;
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

	public static class PythonNode extends Node {

		public Optional<Boolean> correct = Optional.empty();
		public String student;
		public String source;

		@SuppressWarnings("unused")
		private PythonNode() {
			this(null, null, null, null);
		}

		public PythonNode(Node parent, String type, String value, String id) {
			super(parent, type, value, id);
		}

		@Override
		public Node constructNode(Node parent, String type, String value, String id) {
			return new PythonNode(parent, type, value, id);
		}

		public static boolean typeHasBody(String type) {
			return "list".equals(type);
		}

		@Override
		protected boolean nodeTypeHasBody(String type) {
			return typeHasBody(type);
		}

		public ASTSnapshot toASTSnapshot() {
			return super.toASTSnapshot(correct.orElse(false), source);
		}

		@Override
		public Node shallowCopy(Node parent) {
			PythonNode copy = (PythonNode) super.shallowCopy(parent);
			copy.source = this.source;
			copy.student =this.student;
			copy.correct = this.correct;
			return copy;

		}
	}
}
