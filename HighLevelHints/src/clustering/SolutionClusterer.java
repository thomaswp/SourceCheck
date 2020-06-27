package clustering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import alignment.JavaSolutionConfig;
import alignment.SolutionAlignment;
import alignment.SolutionDistanceMeasure;
import edu.isnap.node.JavaNode;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;
import edu.isnap.java.JavaHintConfig;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Suggestion;
import net.sf.javaml.core.*;

public class SolutionClusterer {
	private List<Node> solutions;
	private HintConfig solConfig = new JavaSolutionConfig();
	private String assignment;
	
	public SolutionClusterer(List<Node> solutions, String assignment) {
		this.solutions = solutions;
		this.assignment = assignment;
	}
	
	public int getNumSolutions() {
		return solutions.size();
	}
	
	public HashMap<String, HashMap<String, Instance>> clusterSolutions() {
		// Sort solutions by studentID and timestamp
		Collections.sort(solutions, new Comparator<Node>() {
			@Override
			public int compare(Node o1, Node o2) {
				int studentID = o1.getStudentID().compareTo(o2.getStudentID());
				if (studentID != 0) return studentID;
				return o1.getSubmissionTime().compareTo(o2.getSubmissionTime());
			}			
		});
		
		// Map student ID and timestamp to a distance vector
		HashMap<String, HashMap<String, Instance>> solutionMaps = new HashMap<>();
		
		// Construct a distance matrix
		double max = Double.NEGATIVE_INFINITY;
		double min = Double.POSITIVE_INFINITY;
		JavaHintConfig config = new JavaHintConfig();
		double[][] distanceMatrix = new double[solutions.size()][];
		for (int i = 0; i < solutions.size(); i++) {
			TextualNode solution = (TextualNode) solutions.get(i);
			double[] distances = getDistances(solution).stream().mapToDouble(d -> d).toArray();
//			double[] distances = new double[solutions.size()];
//			for (int j = 0; j < solutions.size(); j++) {
//				List<Node> trace  = new ArrayList<>();
//				trace.add(solutions.get(j));
//				HintData hintData = new HintData(assignment, config, 0, HintHighlighter.DataConsumer);
//				hintData.addTrace(solutions.get(j).getStudentID(), trace);
//				HintHighlighter highlighter = hintData.hintHighlighter();
//
//				highlighter.trace = NullStream.instance;
//
//				List<EditHint> edits = highlighter.highlightWithPriorities(solution);
//				List<Suggestion> suggestions = SourceCodeHighlighter.getSuggestions(edits);
//				int dist = suggestions.size();
//				distances[j] = dist;
//				if (dist > max) max = dist;
//				if (dist < min) min = dist;
//			}
			for (double dist : distances) {
				if (dist > max) max = dist;
				if (dist < min) min = dist;
			}
			distanceMatrix[i] = distances;
		}
		
		// Check if the matrix is symmetric
		for (int i = 0; i < distanceMatrix.length; i++) {
			for (int j = 0; j < i; j++) {
				if (distanceMatrix[i][j] != distanceMatrix[j][i]) {
					System.err.println(distanceMatrix[i][j] + " is not " + distanceMatrix[j][i] 
							+ " when i = " + i + ", j = " + j);
				}
				double smaller = Math.min(distanceMatrix[i][j], distanceMatrix[j][i]);
				distanceMatrix[i][j] = smaller;
				distanceMatrix[j][i] = smaller;
			}
		}
		
		// Normalize dataset
		Dataset normalizedData = new DefaultDataset();
		for (int i = 0; i < solutions.size(); i++) {
			TextualNode solution = (TextualNode) solutions.get(i);
			Instance data = new SparseInstance(distanceMatrix[i]);
			Instance normalized = data.minus(min).divide(max - min);
			if (solutionMaps.get(solution.getStudentID()) == null) {
				solutionMaps.put(solution.getStudentID(), new HashMap<String, Instance>());
			}
			solutionMaps.get(solution.getStudentID()).put(solution.getSubmissionTime(), normalized);
			normalizedData.add(normalized);
		}
		
		PreComputed precomputed = new PreComputed(normalizedData);
		Dataset[] clusters = DBSCAN.fit_predict(normalizedData, 0.12, 6, precomputed);
		for (int i = 0; i < clusters.length; i ++) {
			for (Instance inst : clusters[i]) {
				inst.setClassValue(String.valueOf(i));
			}
		}
		System.out.println("Finished Clustering!");
		return solutionMaps;
	}
	
	private List<Double> getDistances(Node node) {
		DistanceMeasure dm = new SolutionDistanceMeasure(solConfig);
		
		Mapping[] mappings = getMappingsFrom(node, solutions, dm, solConfig);
		List<Double> distances = new ArrayList<Double>();
		for (Mapping map : mappings) {
			distances.add(map.cost());
		}

		return distances;
	}
	
	private static Mapping[] getMappingsFrom(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure, HintConfig config) {
		boolean v2 = config.sourceCheckV2;

		Mapping[] mappings = new Mapping[matches.size()];
		for (int i = 0; i < mappings.length; i++) {
			Node to = matches.get(i);
			NodeAlignment align = new NodeAlignment(from, to, config);
			Mapping mapping = v2 ? align.align(distanceMeasure) :
				align.calculateMapping(distanceMeasure);
			mappings[i] = mapping;
		}

		return mappings;
	}
	
}