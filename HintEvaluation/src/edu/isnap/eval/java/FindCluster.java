package edu.isnap.eval.java;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONObject;

import edu.isnap.node.JavaNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.github.javaparser.javaparser_symbol_solver_core.ASTParserJSON;

import edu.isnap.node.Node;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.Reader;
import edu.isnap.node.TextualNode;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.sourcecheck.edit.Suggestion;

//import edu.isnap.hint.util.NullStream;
public class FindCluster {
	public static int task;
	public static int clusterid;
	private static HashMap<String, Integer> clusterMap = new HashMap<>();
	public static HintData hintData;
	public static TreeMap<Integer, String> hintTree;
//	public static String dataDir = "../data/S20_3.3_OPE_Grading_Anon/3.3_OPE_Submissions-anonymized/"; //for local run
	public static String dataDir = "./data/S20_3.3_OPE_Grading_Anon/3.3_OPE_Submissions-anonymized/"; 
	public static String separator = "@andrew.cmu.edu_data-consistency-ope_consistency-ope-task_";
	public static String[] assignments = {"BankUserConcurrentGet", "BankUserConcurrentPut", "BankUserMultiThreaded", "BankUserStrongConsistency"};
	public static String sourcePath = "/src/main/java/Project_OMP/BankUserSystem/"; // The path to the source file folder for each correct student submission

	public static String DELETE_START = "This code may be incorrect ";
	public static String REPLACE_START = "This code may need to be replaced with something else :";
	public static String CANDIDATE_START = "This code is good, but it may be in the wrong place : ";
	/**
	 * Export solution nodes with pre-computed clusterIDs as List<Node> 
	 * @param inputCSV
	 * @return
	 */
	public static void getSolutionNodes(String inputCSV) throws IOException {
		List<String[]> csvRecords = readCSV(inputCSV);
		LinkedHashMap<String, List<JavaNode>> correctTraces = new LinkedHashMap<>();
		String assignment = assignments[task - 1];
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
	 * Get the JavaNode for the new  student code
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
	 * Returns Hints 
	 * @param studentCodePath
	 * @param taskID
	 * @param level
	 * @param numHints
	 * @return
	 * @throws IOException
	 * 
	 * Reference class files: 
	 * path:	/CTD/src/edu/isnap/sourcecheck/edit/EditHint.java
	 * 
	 */
	public static int getClusterID(String studentCodePath, int taskID, int level, int numHints) throws IOException {
		
		String pth = dataDir + "input" + taskID + ".csv" ;
		getSolutionNodes(pth);
		TextualNode studentCode = (TextualNode) preprocess(studentCodePath);
		
		HintHighlighter highlighter = hintData.hintHighlighter();
		highlighter.trace = NullStream.instance;
		Mapping mapping = highlighter.findSolutionMapping(studentCode);
		List<EditHint> edits = highlighter.highlightWithPriorities(studentCode);
//		String marked = studentCode.getSource();
//		List<String> missing = new ArrayList<>();

		List<Suggestion> suggestions = new ArrayList<>();
		for (EditHint hint : edits) {
			hint.addSuggestions(suggestions);
		}
		Collections.sort(suggestions, (s1, s2) -> s1.location.line - s2.location.line);
		//suggestion has locations (line, col), hint and type
		Set<Integer> suggestionSet = new HashSet<>();
		for (Suggestion suggestion : suggestions) {
			if (numHints < 0) break;
			if (suggestion.start) {
				SourceLocation location = suggestion.location;
				EditHint ed = suggestion.hint; 
				if (!suggestionSet.contains(location.line)) {
					suggestionSet.add(location.line);
					
					//low-level hints
					if (level == 0) {
						switch (suggestion.type) {
							case DELETE:
								System.out.println("line " + location.line + "," + location.col+ ":" + DELETE_START);
//								marked = location.markSource(marked, DELETE_START);
								break;
							case MOVE:
								System.out.println("line " + location.line + ":" + CANDIDATE_START);
//								marked = location.markSource(marked, CANDIDATE_START);
								break;
							case REPLACE:
								System.out.println("line " + location.line + ":" + REPLACE_START);
//								marked = location.markSource(marked, REPLACE_START);
								break;
							case INSERT:
								
								String insertionLine = getInsertHint((Insertion)ed, mapping.config);
								String insertionCode = getTextToInsert((Insertion)ed, mapping);
								System.out.println("line " + location.line + ":" + insertionLine + "\n" +insertionCode);
								System.out.println();
//								marked = location.markSource(marked, insertionCode);
//								missing.add(getHumanReadableName((Insertion) ed, mapping.config));
						}
//						if (!missing.isEmpty()) {
//							marked += "\n\nYou may be missing the following:";
//							for (String m : missing) marked +=  m + "\n";
//						}
//						System.out.println("low level:\n\n " + marked);
					}
					//high-level hints
					if (level == 1) {
						Integer key = hintTree.floorKey(location.line);
						if (key == null) key = hintTree.ceilingKey(location.line);
						String highLvl = hintTree.get(key);
						System.out.println("line " + location.line + ":" + highLvl);
					}
					numHints--;
				}
			}
		}
		Node target = mapping.to;
		return clusterMap.get(target.id);
	}
	
	
	/**
	 * 
	 * @param insertion
	 * @param config
	 * @return
	 */
	private static String getInsertHint(Insertion insertion, HintConfig config) {
		String hrName = getHumanReadableName(insertion, config);
		String hint = "You may need to add " + hrName + " here";
		if (insertion.replaced != null) {
			hint += ", instead of what you have.";
		} else {
			hint += ".";
		}
		return StringEscapeUtils.escapeHtml(hint);
	}
	
	/**
	 * 
	 * @param insertion
	 * @param config
	 * @return
	 */
	private static String getHumanReadableName(Insertion insertion, HintConfig config) {
		String hrName = config.getHumanReadableName(insertion.pair);
		if (insertion.replaced != null && insertion.replaced.hasType(insertion.type)) {
			hrName = hrName.replaceAll("^(an?)", "$1 different");
//			System.out.println(hrName);
		}
		return hrName;
	}
	
	/**
	 * 
	 * @param insertion
	 * @param mapping
	 * @return
	 */
	private static String getTextToInsert(Insertion insertion, Mapping mapping) {
		// TODO: Also need to handle newlines properly
		Node mappedPair = insertion.pair.applyMapping(mapping);
//		System.out.println("Pair:\n" + mappedPair);
		String source = ((TextualNode) mappedPair).getSource();
		if (source != null) return source;
		return insertion.pair.prettyPrint().replace("\n", "");
	}
	
	/**
	 * MAIN Function
	 * @param args
	 * @throws IOException 
	 */
	public static void main (String[] args) throws IOException {
//		String classpathStr = System.getProperty("java.class.path"); //Uncomment line to get the classpaths
//		System.out.println("path: " + classpathStr); //Uncomment line to print the classpaths
		String studentCodePath = args[0];   //path to student source code ".java" file
		System.out.println("studentcodepath: " + studentCodePath);
		String annotatedFilePath = args[1]; //"../data/S20_3.3_OPE_Grading_Anon/task4_annotated.txt"
		System.out.println("annotatedFilePath: " + annotatedFilePath);
		int taskID = Integer.parseInt(args[2]);  // task ID
		System.out.println("taskID: " + taskID);
		int level = Integer.parseInt(args[3]);   // high level hints = 1; low level hints = 0
		System.out.println("level: " + level);
		int numHints = Integer.parseInt(args[4]);  // max number of hints to show for any given snapshot
		System.out.println("numHints: " + numHints);
		
		
//		int taskID = 4;
//		String studentCodePath =
//"../data/S20_3.3_OPE_Grading_Anon/3.3_OPE_Submissions-anonymized/13061@andrew.cmu.edu_data-consistency-ope_consistency-ope-task_20200303225053/src/main/java/Project_OMP/BankUserSystem/BankUserStrongConsistency.java";
//		int level = 0, numHints = 15; //level 1 high, level 0 low 
		
		//option1
		//make jar file: https://cwiki.apache.org/confluence/display/MAVEN/Tutorial%3A+Build+a+JAR+file+with+Maven+in+5+minutes 
		
		
		//option2
		//can just right click project > run as > Run Configurations > java jre
		
		
		//resolve maven error: 
		//step1: export M2_HOME="~/Downloads/apache-maven-3.6.3"
		//step2:  PATH="${M2_HOME}/bin:${PATH}"
		//step3: export PATH
		//step 4: mvn -version
		
		//To set default jvm path:
//		On command line run - "/usr/libexec/java_home -V "
//		Set the default version by  "export JAVA_HOME=`/usr/libexec/java_home -v $version` "
		
		task = taskID;
		hintTree = new TreeMap<>();
		
		if (level == 1) {
			File annotated = new File(annotatedFilePath);
			String jsonString = new String(Files.readAllBytes(annotated.toPath()));
			JSONObject hlvlHints = new JSONObject(jsonString);
			@SuppressWarnings("unchecked")
			Iterator<String> k = hlvlHints.keys();
			while (k.hasNext()) {
				String key = k.next();
				int line = Integer.parseInt(key);
				String hint = hlvlHints.getString(key);
				hintTree.put(line, hint);
			}
		}
		
		clusterid = getClusterID(studentCodePath, taskID, level, numHints);
		System.out.println("cluster: " + clusterid);
		
	}
	
}