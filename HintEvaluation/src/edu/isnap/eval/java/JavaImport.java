package edu.isnap.eval.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.HintData;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.node.Node;
import edu.isnap.python.PythonHintConfig;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.util.map.ListMap;

public class JavaImport {

	public static void main(String[] args) throws IOException {

		// Run generate hints to load data, generate hints for each student and print them out
		// You need to update the file path to wherever you unzipped the data
		generateHints("../../../../Desktop", "anyLowercase");
	}

	static HintData createHintData(String dataDir, String assignment) throws IOException {
		ListMap<String, JavaNode> attempts = loadAssignment(dataDir, assignment);
		return createHintData(assignment, attempts);
	}

	static HintData createHintData(String assignmentID, ListMap<String, JavaNode> attempts) {
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

	// Don't worry about this method for now
	static void serializeHintData(String dataDir, String assignment, String outputPath)
			throws IOException {
		ListMap<String, JavaNode> attempts = loadAssignment(dataDir, assignment);
		List<String> toRemove = new ArrayList<String>();
		// Remove incorrect attempts before serializing
		for (String attemptID : attempts.keySet()) {
			List<JavaNode> attempt = attempts.get(attemptID);
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
		ListMap<String, JavaNode> attempts = loadAssignment(dataDir, assignment);
		for (String student : attempts.keySet()) {
			// May want to change this to a random attempt, not just the first one, but you can
			// start with the first one
			JavaNode firstAttempt = attempts.get(student).get(0);
			if (firstAttempt.correct.orElse(false)) continue;

			ListMap<String, JavaNode> subset = new ListMap<>();
			for (String attemptID : attempts.keySet()) {
				// Get the sequence of snapshots over time
				List<JavaNode> trace = attempts.get(attemptID);
				// If it was correct, then add it to the subset
				if (trace.get(trace.size() - 1).correct.orElse(false)) {
					subset.put(attemptID, attempts.get(attemptID));
				}
			}
			// Remove the student we're generating hints for, because you can't give yourself a hint
			subset.remove(student);

			// We create a "HintData" object, which represents the data from which we generate all
			// hints
			HintData hintData = createHintData(assignment, subset);

			// Then we use this method to "highlight" the java source code using the SourceCheck
			// hints
			System.out.println(SourceCodeHighlighter.highlightSourceCode(
					hintData, firstAttempt));
		}

	}

	// TODO: Modify this so that it loads from your spreadsheet instead of a folder of folders
	static ListMap<String, JavaNode> loadAssignment(String dataDir, String assignment)
			throws IOException {
		ListMap<String, JavaNode> nodes = new ListMap<>();
		for (File studentDir : new File(dataDir, assignment).listFiles(File::isDirectory)) {
			String student = studentDir.getName();
			for (File file : studentDir.listFiles()) {
				if (!file.getName().endsWith(".json")) continue;
				JavaNode node;
				try {
					String source = null;
					File sourceFile = new File(studentDir, file.getName().replace(".json", ".py"));
					if (sourceFile.exists()) {
						source = new String(Files.readAllBytes(sourceFile.toPath()));
					}
					String json = new String(Files.readAllBytes(file.toPath()));
					JSONObject obj = new JSONObject(json);
					ASTSnapshot astNode = ASTSnapshot.parse(obj, source);
					node = (JavaNode) JsonAST.toNode(astNode, JavaNode::new);
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
//				System.out.println(node.toNode(JavaNode::new).prettyPrint(true));
				nodes.add(student, node);
			}
		}
		return nodes;
	}
}
