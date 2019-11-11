package edu.isnap.eval.python;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import edu.isnap.hint.HintData;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.node.Node;
import edu.isnap.python.PythonHintConfig;
import edu.isnap.python.PythonNode;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.python.TextualNode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.util.map.ListMap;

public class PythonImport {

	public static void main(String[] args) throws IOException {
//		generateHints("../../PythonAST/data/datacamp", "65692");
//		generateHints("../../PythonAST/data/itap", "firstAndLast");
		serializeHintData("../../PythonAST/data/PCRS", "8",
				"../HintServer/WebContent/WEB-INF/data/8.hdata");
//		generateHints("../../PythonAST/data/pcrs", "8");

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

	static HintData createHintData(String dataDir, String assignment) throws IOException {
		ListMap<String, PythonNode> attempts = loadAssignment(dataDir, assignment);
		return createHintData(assignment, attempts);
	}

	static HintData createHintData(String assignmentID, ListMap<String, PythonNode> attempts) {
		PythonHintConfig config = new PythonHintConfig();
		HintData hintData = new HintData(assignmentID, config, 1, HintHighlighter.DataConsumer);
		for (String attemptID : attempts.keySet()) {
			List<Node> trace = attempts.get(attemptID).stream()
					.map(node -> (Node) node)
					.collect(Collectors.toList());
			// Only needed for LOOCV
			hintData.addTrace(attemptID, trace);
		}
		return hintData;
	}

	static void serializeHintData(String dataDir, String assignment, String outputPath)
			throws IOException {
		ListMap<String, PythonNode> attempts = loadAssignment(dataDir, assignment);
		List<String> toRemove = new ArrayList<String>();
		// Remove incorrect attempts before serializing
		for (String attemptID : attempts.keySet()) {
			List<PythonNode> attempt = attempts.get(attemptID);
			if (attempt.size() == 0 || !attempt.get(attempt.size() - 1).correct.orElse(false)) {
				toRemove.add(attemptID);
			}
		}
		toRemove.forEach(attempts::remove);
		HintData hintData = createHintData(assignment, attempts);
		Kryo kryo = SnapHintBuilder.getKryo();
		Output output = new Output(new FileOutputStream(outputPath));
		kryo.writeObject(output, hintData);
		output.close();
	}

	static void generateHints(String dataDir, String assignment) throws IOException {
		ListMap<String, PythonNode> attempts = loadAssignment(dataDir, assignment);
		for (String student : attempts.keySet()) {
			PythonNode firstAttempt = attempts.get(student).get(0);
			if (firstAttempt.correct.orElse(false)) continue;

			ListMap<String, PythonNode> subset = new ListMap<>();
			for (String attemptID : attempts.keySet()) {
				List<PythonNode> trace = attempts.get(attemptID);
				if (trace.get(trace.size() - 1).correct.orElse(false)) {
					subset.put(attemptID, attempts.get(attemptID));
				}
			}
			subset.remove(student);
			HintData hintData = createHintData(assignment, subset);

			System.out.println(SourceCodeHighlighter.highlightSourceCode(
					hintData, firstAttempt));
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
					node = (PythonNode) TextualNode.fromJSON(obj, source, PythonNode::new);
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
}
