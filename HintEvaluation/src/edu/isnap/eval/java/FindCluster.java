package edu.isnap.eval.java;

import org.json.JSONObject;

import edu.isnap.node.JavaNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.javaparser_symbol_solver_core.ASTParserJSON;

import edu.isnap.node.Node;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.Reader;
import edu.isnap.node.TextualNode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;


public class FindCluster {
	public static int task = 1;
	private static HashMap<String, Integer> clusterMap = new HashMap<>();
	public static HintData hintData;
	public static String dataDir = "./3.3_OPE_Submissions-anonymized/";
	public static String separator = "@andrew.cmu.edu_data-consistency-ope_consistency-ope-task_";
	public static String[] assignments = {"BankUserConcurrentGet", "BankUserConcurrentPut", "BankUserMultiThreaded", "BankUserStrongConsistency"};
	public static String assignment = assignments[task - 1];
	public static String sourcePath = "/src/main/java/Project_OMP/BankUserSystem/"; // The path to the source file folder for each student

	/**
	 * Export solution nodes with pre-computed clusterIDs as List<Node> 
	 * @param inputCSV
	 * @return
	 */
	public static void getSolutionNodes(String inputCSV) throws IOException {
		List<String[]> csvRecords = readCSV(inputCSV);
		LinkedHashMap<String, List<JavaNode>> correctTraces = new LinkedHashMap<>();
		
		for (String[] record : csvRecords) {
			String timestamp = record[0];
			String studentID = record[1];
			String isCorrect = record[10];
//			String isAnnotated = record[13];
			String clusterID = record[14];
			
			if (isCorrect.toLowerCase().equals("true")) {
				if (!clusterID.equals("")) {
					File originalCode = new File(dataDir + studentID + separator + timestamp + sourcePath + assignment + ".java");
					String originalSourceCode = new String(Files.readAllBytes(originalCode.toPath()));
					String jsonString = ASTParserJSON.toJSON(originalSourceCode);
					JSONObject parsedTree = new JSONObject(jsonString);
		
					JavaNode node = (JavaNode)JavaNode.fromJSON(parsedTree, studentID, originalSourceCode, JavaNode::new);
//				    node.correct = Optional.of(true);
//				    node.cluster = Optional.of((int)Float.parseFloat(clusterID));

				    if (!correctTraces.containsKey(studentID)) {
				    	clusterMap.put(studentID, (int)Float.parseFloat(clusterID));
				    	correctTraces.put(studentID , new ArrayList<JavaNode>());
				    }
				    correctTraces.get(studentID).add(node);
				}
			}		
		}
		hintData = JavaImport.createHintData(assignment, correctTraces);
		
		assert correctTraces.size() != 0 : "No correct Solution Nodes exist";

	}
	
	
	
	/**
	 * readCSV
	 * @param fileName
	 * @return
	 */
	private static List<String[]> readCSV(String fileName) {
		List<String[]> csvRecords = new ArrayList<>();
		try (Reader reader = Files.newBufferedReader(Paths.get(fileName));
				CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
			String[] nextLine;
			while ((nextLine = csvReader.readNext()) != null) {
				String projectID = nextLine[1];
				String sourceFileID = nextLine[2];
				String compileTime = nextLine[3];
				String filePath = nextLine[4];
				String sourceCode = nextLine[5];
				String diff = nextLine[6];
				String isLastDiff = nextLine[7];
				String sessionID = nextLine[8];
				String compileID = nextLine[9];
				String originalFileID = nextLine[10];
				String isCorrect = nextLine[11];
				String doesCompile = nextLine[12];
				String sourceCodeJSON = nextLine[13];
				String isAnnotated = nextLine[14];
				String clusterID = nextLine[15];
				String[] record = { projectID, sourceFileID, compileTime, filePath, sourceCode, diff, isLastDiff,
						sessionID, compileID, originalFileID, isCorrect, doesCompile, sourceCodeJSON, isAnnotated, clusterID };
				csvRecords.add(record);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return csvRecords;
	}
	

	
	/**
	 * Get the JavaNode for the student code
	 * @param studentCodePath
	 * @return
	 */
	public static JavaNode preprocess(String studentCodePath) throws IOException {
		File originalCode = new File(studentCodePath);
		String originalSourceCode = new String(Files.readAllBytes(originalCode.toPath()));
		String jsonString = ASTParserJSON.toJSON(originalSourceCode);
		JSONObject parsedTree = new JSONObject(jsonString);
		
		JavaNode node = (JavaNode) JavaNode.fromJSON(parsedTree, originalSourceCode, JavaNode::new);
		
		return node;
	
	}
	
	
	/**
	 * Return closest cluster id the student solution belongs to
	 * @param studentCodePath
	 * @return
	 * @throws IOException
	 */
	public static int getClusterID(String studentCodePath, int studentID) throws IOException {
		TextualNode studentCode = (TextualNode) preprocess(studentCodePath);
		
		HintHighlighter highlighter = hintData.hintHighlighter();
		highlighter.trace = NullStream.instance;
		Mapping mapping = highlighter.findSolutionMapping(studentCode);
		Node target = mapping.to;
		System.out.println("Student " + studentID + " :cluster similar to studentID: " + target.id);

		return clusterMap.get(target.id);
	}
	
//	/**
//	 * Uncomment main to get all classpaths
//	 * @param args
//	 */
//	public static void main (String[] args) {
//		String classpathStr = System.getProperty("java.class.path");
//		System.out.println("path: " + classpathStr);
//
//	}

	
}