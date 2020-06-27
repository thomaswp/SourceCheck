package edu.isnap.eval.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;

import clustering.SolutionClusterer;

import com.github.javaparser.javaparser_symbol_solver_core.ASTParserJSON;

import edu.isnap.hint.HintData;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.java.JavaHintConfig;
import edu.isnap.node.JavaNode;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.node.Node.Annotations;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.edit.EditHint;
//import edu.isnap.util.map.ListMap;

import net.sf.javaml.core.*;

public class JavaImport {

	static String DATA_DIR = "../data/F19_Project_3_2/task4/";
	static String CMU_CS = "@andrew.cmu.edu_social-network_p32-task4_";
	static boolean clustered = false;

	public static void main(String[] args) throws IOException {

		// Run generate hints to load data, generate hints for each student and print
		// them out
		// You need to update the file path to wherever you unzipped the data

		generateHintsForGS(DATA_DIR + "input.csv", "TimelineServlet");
	}

	/*
	 * static HintData createHintData(String inputCSV, String assignment) throws
	 * IOException { LinkedHashMap<String, List<JavaNode>> attempts =
	 * loadAssignment(inputCSV); return createHintData(assignment, attempts); }
	 */

	/**
	 * 
	 * @param assignment the name of java file used in this assignment. Omit
	 *                   ".java".
	 * @param attempts   Hashmap that maps student id's to LinkedHashMap's (i.e.
	 *                   each student has their own LinkedHashMap). Each
	 *                   LinkedHashMap maps timestamps to parsed JavaNodes
	 * 
	 * @return
	 */
	static HintData createHintData(String assignment, LinkedHashMap<String, List<JavaNode>> attempts) {
		JavaHintConfig config = new JavaHintConfig();
		HintData hintData = new HintData(assignment, config, 0, HintHighlighter.DataConsumer);
		for (String studentID : attempts.keySet()) {
			List<Node> trace = attempts.get(studentID).stream().map(node -> (Node) node).collect(Collectors.toList());
			// Only needed for LOOCV
			hintData.addTrace(studentID, trace);
		}
		return hintData;
	}

	/*
	 * static void generateHints(String inputCSV, String assignment) throws
	 * IOException { HashMap<String, LinkedHashMap<String, List<JavaNode>>>
	 * filePathToattempts = loadAssignment( inputCSV, false, assignment);
	 * 
	 * for (String filePath : filePathToattempts.keySet()) { File startSourceFile =
	 * new File(DATA_DIR + "Start/" + filePath); String startSource = ""; if
	 * (startSourceFile.exists()) { startSource = new
	 * String(Files.readAllBytes(startSourceFile.toPath())); startSource =
	 * stripComments(startSource); }
	 * 
	 * // For now, just look at ClockDisplay, since NumberDisplay didn't have to be
	 * edited //if (!filePath.equals("ClockDisplay.java")) continue;
	 * LinkedHashMap<String, List<JavaNode>> attempts =
	 * filePathToattempts.get(filePath); for (String student : attempts.keySet()) {
	 * // May want to change this to a random attempt, not just the first one, but
	 * you can // start with the first one JavaNode firstAttempt =
	 * attempts.get(student).get(0); if (firstAttempt.correct.orElse(false))
	 * continue;
	 * 
	 * LinkedHashMap<String, List<JavaNode>> subset = new LinkedHashMap<>(); for
	 * (String attemptID : attempts.keySet()) { // Don't add this student to their
	 * own hint generation data if (attemptID.equals(student)) continue; // Get the
	 * sequence of snapshots over time List<JavaNode> trace =
	 * attempts.get(attemptID); // If it was correct, then add it to the subset if
	 * (trace.get(trace.size() - 1).correct.orElse(false)) { // String
	 * solutionSource = stripComments( // trace.get(trace.size() - 1).getSource());
	 * // System.out.println("Solution #: " + subset.size()); //
	 * System.out.println(Diff.diff(startSource, solutionSource, 2)); //
	 * System.out.println("--------------------------"); subset.put(attemptID,
	 * attempts.get(attemptID)); } } // if (1==1) break;
	 * System.out.println("Student: " + student);
	 * System.out.println("Building with: " + subset.size()); // We create a
	 * "HintData" object, which represents the data from which we generate // all
	 * hints HintData hintData = createHintData(assignment, subset);
	 * 
	 * // Then we use this method to "highlight" the java source code using the
	 * SourceCheck // hints System.out.println(
	 * SourceCodeHighlighter.highlightSourceCode(hintData, firstAttempt));
	 * 
	 * break; } } }
	 */
	
	static void clusterSolutions(List<Node> correct, String assignment) throws IOException {
		SolutionClusterer clusterer = new SolutionClusterer(correct, assignment);
		HashMap<String, HashMap<String, Instance>> solutionMaps = clusterer.clusterSolutions();
		List<String> students = new ArrayList<String>(solutionMaps.keySet());
		Collections.sort(students);
		
		// Open a csv file
		File clusterCSV = new File(DATA_DIR + "cluster_info.csv");
		FileWriter outputfile = new FileWriter(clusterCSV); 
		  
        // create CSVWriter object filewriter object as parameter 
        CSVWriter writer = new CSVWriter(outputfile); 
  
        // adding header to csv 
        List<String> header = new ArrayList<String>();
        header.add("StudentID");
        header.add("Timestamp"); 
        header.add("ClusterID");
        for (int i = 1; i <= clusterer.getNumSolutions(); i++) {
        	header.add("Dist_" + i);
        }
        String[] tempHeader = new String[header.size()];
        writer.writeNext(header.toArray(tempHeader)); 
  
        // add data to csv 
        for (String student : students) {
        	HashMap<String, Instance> timeMap = solutionMaps.get(student);
        	List<String> timestamps = new ArrayList<String>(timeMap.keySet());
        	String timestamp = timestamps.get(0);
//        	Collections.sort(timestamps);
//        	for (String timestamp : timestamps) {
        		List<String> data = new ArrayList<String>();
        		Instance inst = timeMap.get(timestamp);
        		String clusterID = (String) inst.classValue();
        		data.add(student); // student id
        		data.add(timestamp); // timestamp
        		data.add(clusterID); // cluster id
        		
        		// Add distances
        		Iterator<Double> itr = inst.iterator();
    			while (itr.hasNext()) {
    				data.add(itr.next().toString());
    			}
    			String[] tempData = new String[data.size()];
    			writer.writeNext(data.toArray(tempData));
//        	}
        }
  
        // closing writer connection 
        writer.close(); 
	}

	/**
	 * Exports html files highlighting hints
	 * 
	 * @param inputCSV   path to your input csv containing student id's, timestamps,
	 *                   correctness, etc
	 * @param assignment the name of java file used in this assignment. Omit
	 *                   ".java".
	 * @throws IOException
	 */
	static void generateHintsForGS(String inputCSV, String assignment) throws IOException {
		HashMap<String, LinkedHashMap<String, JavaNode>> attempts = loadAssignment(inputCSV, true, assignment);

		// Maps student id's to their history of submissions. Only students
		// who eventually got correct are considered
		LinkedHashMap<String, List<JavaNode>> correctTraces = new LinkedHashMap<>();
		// List of correct submissions
		List<Node> correct = new ArrayList<>();
		LinkedHashMap<Integer, JavaNode> annotated = new LinkedHashMap<>();
		for (String studentID : attempts.keySet()) {
			List<JavaNode> trace = new ArrayList<>();
			List<String> timestamps = new ArrayList<>(attempts.get(studentID).keySet());
			Collections.sort(timestamps);
			for (String timestamp : timestamps) {
				// Get the sequence of snapshots over time
				JavaNode node = attempts.get(studentID).get(timestamp);
//				if (node.correct.orElse(false)) {
//					correct.add(node);
//				}
				trace.add(node);
				if (!node.readOnlyAnnotations().equals(Annotations.EMPTY)) {
					annotated.put(node.cluster.get(), node);
				}
			}

			// If it was correct, then add it to the subset
			if (trace.get(trace.size() - 1).correct.orElse(false)) {
				correctTraces.put(studentID, trace);
				correct.add(trace.get(trace.size() - 1));
			}
		}
		HintData hintData = createHintData(assignment, correctTraces);
		if (!clustered) {
			clusterSolutions(correct, assignment);
	        return;
		}
		
		for (int clusterID : annotated.keySet()) {
			hintData.addReferenceSoltion(clusterID, annotated.get(clusterID));
		}

		// TODO: Get the actual list from a .csv file, map project_id to the hint
		// request
		List<String[]> csvRecords = readCSV(inputCSV);
		for (String[] record : csvRecords) {
			// for (String student : attempts.keySet()) {
			String timestamp = record[0];
			String student = record[1];
			JavaNode hintRequest = attempts.get(student).get(timestamp);
			String highlightedCode = SourceCodeHighlighter.highlightSourceCode(hintData, hintRequest);
			highlightedCode = highlightedCode.replace("\n", "<br>\n");
			highlightedCode = "<meta http-equiv=\"content-type\" charset=\"utf-8\">\n" + "<link rel = \"stylesheet\"\n"
					+ "   type = \"text/css\"\n" + "   href = \"../../../style.css\" />\n" + highlightedCode;
			PrintWriter out = new PrintWriter(DATA_DIR + student + CMU_CS + timestamp + "/output_hints.html");
			out.println(highlightedCode);
			out.close();

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

	public static List<String[]> readCSV(String fileName) {
		List<String[]> csvRecords = new ArrayList<>();

		try (Reader reader = Files.newBufferedReader(Paths.get(fileName));
				CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
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
				String isAnnotated = nextLine[13];
				String clusterID = nextLine[14];
				String[] record = { projectID, sourceFileID, compileTime, filePath, sourceCode, diff, isLastDiff,
						sessionID, compileID, originalFileID, isCorrect, doesCompile, sourceCodeJSON, isAnnotated, clusterID };
				csvRecords.add(record);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return csvRecords;
	}

	private static String removeComments(String code) {
		return String.join("\n", Arrays.stream(code.split("\n"))
				.filter(l -> !(l.trim().startsWith("*") || l.trim().startsWith("/**"))).collect(Collectors.toList()));
	}

	/**
	 * @param inputCSV   path to your input csv containing student id's, timestamps,
	 *                   correctness, etc
	 * @param GS         ignored
	 * @param assignment the name of java file used in this assignment. Omit
	 *                   ".java".
	 * 
	 * @return Hashmap that maps student id's to LinkedHashMap's (i.e. each student
	 *         has their own LinkedHashMap). Each LinkedHashMap maps timestamps to
	 *         parsed JavaNodes
	 *
	 */
	static HashMap<String, LinkedHashMap<String, JavaNode>> loadAssignment(String inputCSV, boolean GS,
			String assignment) throws IOException {
		HashMap<String, LinkedHashMap<String, JavaNode>> filePathToNodes = new HashMap<>();
		List<String[]> csvRecords = readCSV(inputCSV);

		clustered = false;
		for (String[] record : csvRecords) {
			String timestamp = record[0];
			String studentID = record[1];
			String isCorrect = record[10];
			String isAnnotated = record[13];
			String clusterID = record[14];

			// JSONObject obj = new JSONObject(json);
			File originalCode = new File(DATA_DIR + studentID + CMU_CS + timestamp + "/" + assignment + ".java");
			String originalSourceCode = new String(Files.readAllBytes(originalCode.toPath()));
			String jsonString;
			if (isAnnotated.toLowerCase().equals("true")) {
				File annotatedCode = new File(DATA_DIR + studentID + CMU_CS + timestamp + "/" + assignment + ".json");
				jsonString = new String(Files.readAllBytes(annotatedCode.toPath()));
			}else {
				jsonString = ASTParserJSON.toJSON(originalSourceCode);
			}
			
			// TODO has to be commented out
//			if ((studentID + CMU_CS + timestamp).equals("84895@andrew.cmu.edu_social-network_p32-task4_20191003042705") || 
//					(studentID + CMU_CS + timestamp).equals("69641@andrew.cmu.edu_social-network_p32-task4_20191013030137") || 
//					(studentID + CMU_CS + timestamp).equals("27319@andrew.cmu.edu_social-network_p32-task4_20191011031955")) {
//				PrintWriter out = new PrintWriter(DATA_DIR + studentID + CMU_CS + timestamp + "/" + assignment + ".json");
//				out.println(jsonString);
//				out.close();
//			}
			
			JSONObject parsedTree = new JSONObject(jsonString);
			// JavaNode node = (JavaNode) TextualNode.fromJSON(obj, sourceCode,
			// JavaNode::new);
			JavaNode node = (JavaNode) JavaNode.fromJSON(parsedTree, originalSourceCode, JavaNode::new);
			if (isCorrect.toLowerCase().equals("true")) {// || GS) {
				node.correct = Optional.of(true);
			}
			if (!clusterID.equals("")) {
				node.cluster = Optional.of(Integer.parseInt(clusterID));
				clustered = true;
			}

			node.setStudentID(studentID);
			node.setSubmissionTime(timestamp);
			
			if (filePathToNodes.get(studentID) == null) {
				filePathToNodes.put(studentID, new LinkedHashMap<String, JavaNode>());
			}
			/*
			 * if(filePathToNodes.get(studentID).get(timestamp) == null) {
			 * filePathToNodes.get(studentID).put(timestamp, node); }
			 */
			filePathToNodes.get(studentID).put(timestamp, node);
			// filePathToNodes.get(studentID).get(timestamp).add(node);
		}

		return filePathToNodes;
	}
}
