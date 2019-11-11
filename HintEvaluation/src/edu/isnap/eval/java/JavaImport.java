package edu.isnap.eval.java;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import edu.isnap.hint.HintData;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.sourcecheck.HintHighlighter;
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
		generateHints(DATA_DIR + "1__output_clock-display-ast.csv", "ClockDisplay");
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
	/*static void serializeHintData(String inputCSV, String assignment, String outputPath)
			throws IOException {
		ListMap<String, JavaNode> attempts = loadAssignment(inputCSV);
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
	}*/

	static void generateHints(String inputCSV, String assignment) throws IOException {
		HashMap<String, ListMap<String, JavaNode>> filePathToattempts = loadAssignment(inputCSV);

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

	static HashMap<String, ListMap<String, JavaNode>> loadAssignment(String inputCSV)
			throws IOException {
		HashMap<String, ListMap<String, JavaNode>> filePathToNodes = new HashMap<>();
		List<String[]> csvRecords= readCSV(inputCSV);
		Set<String> numberDisplayProjects = new HashSet<>();
		File startSourceFile = new File(DATA_DIR + "Start/NumberDisplay.java");
		String numberDisplayStartSource = new String(Files.readAllBytes(startSourceFile.toPath()),
				Charset.forName("UTF-8"));
		numberDisplayStartSource = numberDisplayStartSource.replaceAll("\r", "");
		numberDisplayStartSource = removeComments(numberDisplayStartSource);
		for(String[] record: csvRecords) {
			String projectID = record[0];
			String sourceCode = record[4];
			String sourceCodeJSON = record[12];
			String isCorrect = record[10];
			String filePath = record[3].split("/")[1];
			if(filePath.equals("ClockDisplay.java") || filePath.equals("NumberDisplay.java")) {
				File testJson = new File(DATA_DIR + "ASTs/" + sourceCodeJSON);
				String json = new String(Files.readAllBytes(testJson.toPath()));

				if (filePath.contentEquals("NumberDisplay.java") && isCorrect.equals("True")) {
					String source = removeComments(sourceCode);
					if (!source.equals(numberDisplayStartSource)) {
						numberDisplayProjects.add(projectID);
//						if (Math.random() < 0.05) {
//							System.out.println(Diff.diff(source, numberDisplayStartSource, 2));
//						}
					}
				}

				JSONObject obj = new JSONObject(json);
				JavaNode node = (JavaNode) TextualNode.fromJSON(obj, sourceCode, JavaNode::new);
				if (isCorrect.equals("True")) {
					node.correct = Optional.of(true);
				}
				if(filePathToNodes.get(filePath) == null) {
					filePathToNodes.put(filePath, new ListMap<String, JavaNode>());
				}
				filePathToNodes.get(filePath).add(projectID, node);
			}
		}
		System.out.println("NDPs: " + numberDisplayProjects.size());
		// Remove all solutions that changed the NumberDisplay class
		for (String filePath : filePathToNodes.keySet()) {
			for (String project : numberDisplayProjects) {
				filePathToNodes.get(filePath).remove(project);
			}
		}
		return filePathToNodes;
	}
}
