package edu.isnap.eval.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import edu.isnap.hint.HintData;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.java.JavaHintConfig;
import edu.isnap.node.JavaNode;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.util.map.ListMap;

public class JavaImport {

	static String DATA_DIR = "../data/blackbox/ClockDisplay/";

	public static void main(String[] args) throws IOException {

		// Run generate hints to load data, generate hints for each student and print them out
		// You need to update the file path to wherever you unzipped the data

//		Map<String, ListMap<String, JavaNode>> nodes = loadAssignment(
//				DATA_DIR + "1__output_clock-display-ast.csv");
//		GrammarBuilder builder = new GrammarBuilder("java", new HashMap<>());
//		nodes.values().forEach(listMap -> listMap.values()
//				.forEach(list -> list.forEach(n -> builder.add(n))));
//		System.out.println(builder.toJSON());


		PrintStream fileOut = new PrintStream(DATA_DIR + "output_hints.txt");
//		System.setOut(fileOut);
		generateHintsForGS(DATA_DIR + "1__output_clock-display-ast-gs.csv", "ClockDisplay");
//		serializeHintData(DATA_DIR + "1__output_clock-display-ast.csv", "ClockDisplay",
//				"../HintServer/WebContent/WEB-INF/data/ClockDisplay.hdata");
	}

	/*static HintData createHintData(String inputCSV, String assignment) throws IOException {
		ListMap<String, JavaNode> attempts = loadAssignment(inputCSV);
		return createHintData(assignment, attempts);
	}*/

	static HintData createHintData(String assignmentID, ListMap<String, JavaNode> attempts) {
		JavaHintConfig config = new JavaHintConfig();
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
	static void serializeHintData(String inputCSV, String assignment, String outputPath)
			throws IOException {
		ListMap<String, JavaNode> attempts = loadAssignment(inputCSV, false).get("ClockDisplay.java");
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

	static void generateHints(String inputCSV, String assignment) throws IOException {
		HashMap<String, ListMap<String, JavaNode>> filePathToattempts = loadAssignment(inputCSV, false);

		for (String filePath : filePathToattempts.keySet()) {
			File startSourceFile = new File(DATA_DIR + "Start/" + filePath);
			String startSource = "";
			if (startSourceFile.exists()) {
				startSource = new String(Files.readAllBytes(startSourceFile.toPath()));
				startSource = stripComments(startSource);
			}

			// For now, just look at ClockDisplay, since NumberDisplay didn't have to be edited
			if (!filePath.equals("ClockDisplay.java")) continue;
			ListMap<String, JavaNode> attempts = filePathToattempts.get(filePath);
			for (String student : attempts.keySet()) {
				// May want to change this to a random attempt, not just the first one, but you can
				// start with the first one
				JavaNode firstAttempt = attempts.get(student).get(0);
				if (firstAttempt.correct.orElse(false)) continue;

				ListMap<String, JavaNode> subset = new ListMap<>();
				for (String attemptID : attempts.keySet()) {
					// Don't add this student to their own hint generation data
					if (attemptID.equals(student)) continue;
					// Get the sequence of snapshots over time
					List<JavaNode> trace = attempts.get(attemptID);
					// If it was correct, then add it to the subset
					if (trace.get(trace.size() - 1).correct.orElse(false)) {
//						String solutionSource = stripComments(
//								trace.get(trace.size() - 1).getSource());
//						System.out.println("Solution #: " + subset.size());
//						System.out.println(Diff.diff(startSource, solutionSource, 2));
//						System.out.println("--------------------------");
						subset.put(attemptID, attempts.get(attemptID));
					}
				}
//				if (1==1) break;
				System.out.println("Student: " + student);
				System.out.println("Building with: " + subset.size());
				// We create a "HintData" object, which represents the data from which we generate
				// all hints
				HintData hintData = createHintData(assignment, subset);

				// Then we use this method to "highlight" the java source code using the SourceCheck
				// hints
				System.out.println(
						SourceCodeHighlighter.highlightSourceCode(hintData, firstAttempt));

				break;
			}
		}
	}

	static void generateHintsForGS(String inputCSV, String assignment) throws IOException {
		ListMap<String, JavaNode> attempts =
				loadAssignment(inputCSV, true).get("ClockDisplay.java");
		//System.out.println(attempts);
		ListMap<String, JavaNode> correct = new ListMap<>();
		for (String attemptID : attempts.keySet()) {
			if(attemptID.startsWith("s") || attemptID.startsWith("r")) {
				// Get the sequence of snapshots over time
				List<JavaNode> trace = attempts.get(attemptID);
				// If it was correct, then add it to the subset
				if (trace.get(trace.size() - 1).correct.orElse(false)) {
					correct.put(attemptID, attempts.get(attemptID));
				}
			}
		}
		HintData hintData = createHintData(assignment, correct);
		HintHighlighter highlighter = hintData.hintHighlighter();

		// TODO: Get the actual list from a .csv file, map project_id to the hint request
		ListMap<String, JavaNode> goldStandardHintRequests = attempts;
		new File(DATA_DIR+"GS").mkdirs();
		for (String student : goldStandardHintRequests.keySet()) {
			if(student.startsWith("s") || student.startsWith("r")) {
				continue;
			}
			JavaNode hintRequest = goldStandardHintRequests.get(student).get(0);
			List<EditHint> edits = highlighter.highlightWithPriorities(hintRequest);
			int i = 0;
			for (EditHint hint : edits) {
				Node copy = hintRequest.copy();
				EditHint.applyEdits(copy, Collections.singletonList(hint));
				double priority = hint.priority.consensus();
				JSONObject json = copy.toJSON();
				json.put("priority", priority);
				String file = String.format("%s_%02d.json", student, i);
				PrintWriter out = new PrintWriter(DATA_DIR+"GS/"+file);
				out.println(json.toString());
				out.close();
				System.out.println(file + ": " + json.toString());
				i++;
			}
		}
	}

	private static String stripComments(String source) {
		String[] lines = source.split("\n");
		List<String> l = new ArrayList<>();
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("*") || trimmed.startsWith("/**") || trimmed.startsWith("*/")) {
				continue;
			}
			l.add(line);
		}
		return String.join("\n", l);
	}

	public static List<String[]> readCSV(String fileName){
		List<String[]> csvRecords = new ArrayList<>();

        try (
        		Reader reader = Files.newBufferedReader(Paths.get(fileName));
            	CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()
            ) {
            String[] nextLine;
            while ((nextLine = csvReader.readNext()) != null) {
            	String projectID = nextLine[0];
            	String sourceFileID = nextLine[1];
            	String compileTime = nextLine[2];
            	String filePath = nextLine[3];
            	String sourceCode = nextLine[4];
            	String diff = nextLine[5];
            	String isLastDiff = nextLine[6];
    			String sessionID = nextLine[7];
    			String compileID = nextLine[8];
    			String originalFileID = nextLine[9];
    			String isCorrect = nextLine[10];
    			String doesCompile = nextLine[11];
    			String sourceCodeJSON = nextLine[12];
    			String[] record = {projectID, sourceFileID, compileTime, filePath, sourceCode, diff,
    					isLastDiff, sessionID,
    					compileID, originalFileID, isCorrect, doesCompile, sourceCodeJSON};
    			csvRecords.add(record);
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
		return csvRecords;
	}

	private static String removeComments(String code) {
		return String.join("\n",
				Arrays.stream(code.split("\n"))
				.filter(l -> !(l.trim().startsWith("*") || l.trim().startsWith("/**")))
				.collect(Collectors.toList())
				);
	}

	static HashMap<String, ListMap<String, JavaNode>> loadAssignment(String inputCSV, boolean GS)
			throws IOException {
		HashMap<String, ListMap<String, JavaNode>> filePathToNodes = new HashMap<>();
		List<String[]> csvRecords= readCSV(inputCSV);
		Set<String> numberDisplayProjects = new HashSet<>();;
		String numberDisplayStartSource = null;
		if(!GS) {
			numberDisplayProjects = new HashSet<>();
			File startSourceFile = new File(DATA_DIR + "Start/NumberDisplay.java");
			numberDisplayStartSource = new String(Files.readAllBytes(startSourceFile.toPath()),
					Charset.forName("UTF-8"));
			numberDisplayStartSource = numberDisplayStartSource.replaceAll("\r", "");
			numberDisplayStartSource = removeComments(numberDisplayStartSource);
		}

		for(String[] record: csvRecords) {
			String projectID = record[0];
			String sourceCode = record[4];
			String sourceCodeJSON = record[12];
			String isCorrect = record[10];
			String filePath = record[3];
			if(!GS) {
				filePath = filePath.split("/")[1];
			}
			else {
				filePath = "ClockDisplay.java";
			}
			if(filePath.equals("ClockDisplay.java") || filePath.equals("NumberDisplay.java")) {
				File testJson = new File(DATA_DIR + "ASTs/" + sourceCodeJSON);
				if(GS) {
					testJson = new File(DATA_DIR + "ASTsGS/" + sourceCodeJSON);
				}
				String json = new String(Files.readAllBytes(testJson.toPath()));

				if (!GS && filePath.contentEquals("NumberDisplay.java") && isCorrect.equals("True")) {
					String source = removeComments(sourceCode);
					if (!source.equals(numberDisplayStartSource)) {
						numberDisplayProjects.add(projectID);
					}
				}

				JSONObject obj = new JSONObject(json);
				JavaNode node = (JavaNode) TextualNode.fromJSON(obj, sourceCode, JavaNode::new);
				if (isCorrect.toLowerCase().equals("true") || GS) {
					node.correct = Optional.of(true);
				}

				if(filePathToNodes.get(filePath) == null) {
					filePathToNodes.put(filePath, new ListMap<String, JavaNode>());
				}
				filePathToNodes.get(filePath).add(projectID, node);
			}
		}
		if(!GS) {
			System.out.println("NDPs: " + numberDisplayProjects.size());
		}
		// Remove all solutions that changed the NumberDisplay class (for now)
		// TODO: At some point, we need to use both source files in hint generation...
		for (String filePath : filePathToNodes.keySet()) {
			for (String project : numberDisplayProjects) {
				filePathToNodes.get(filePath).remove(project);
			}
		}
		return filePathToNodes;
	}
}