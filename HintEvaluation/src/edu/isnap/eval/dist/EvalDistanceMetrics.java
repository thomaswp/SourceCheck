package edu.isnap.eval.dist;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.Node;
import edu.isnap.eval.export.ClusterDistance;
import edu.isnap.eval.export.ClusterDistance.NodeDistanceMeasure;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.util.Spreadsheet;

public class EvalDistanceMetrics {
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		HashMap<Integer, Integer> ratings = loadRatings();
		List<Comparison> comparisons = loadComparisons(ratings);
		comparisons.forEach(System.out::println);
		
		Spreadsheet spreadsheet = new Spreadsheet();
		for (Comparison comparison : comparisons) {
			spreadsheet.newRow();
			spreadsheet.put("exampleID", comparison.id);
			spreadsheet.put("left", comparison.left.prettyPrint());
			spreadsheet.put("right", comparison.right.prettyPrint());
			spreadsheet.put("similarity", comparison.similarity);
			for (NodeDistanceMeasure dm : ClusterDistance.DistanceMeasures) {
				spreadsheet.put(dm.name(), dm.measure(comparison.left, comparison.right));
			}
		}
		spreadsheet.write(new FileOutputStream("../data/csc200/all/analysis/distance.csv"));
	}
	
	static class Comparison {
		public final int id;
		public final Node left, right;
		public final int similarity;
		
		public Comparison(int id, Node left, Node right, int similarity) {
			this.id = id;
			this.left = left;
			this.right = right;
			this.similarity = similarity;
		}
		
		@Override
		public String toString() {
			return "Comparison: " + similarity + "\n" + 
					left.prettyPrint() + "\n" + right.prettyPrint();
		}
	}
	
	private static List<Comparison> loadComparisons(HashMap<Integer, Integer> ratings) 
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader("../data/csc200/all/example-code.csv"), 
				CSVFormat.DEFAULT.withHeader());
		
		List<Comparison> comparisons = new ArrayList<Comparison>();
		for (CSVRecord record : parser) {
			int exampleID = Integer.parseInt(record.get("hid"));
			String rowID = record.get("rowID");
			String originalCodeXML = record.get("originCode");
			String startCodeXML = record.get("startCode");
			
			Integer similarity = ratings.get(exampleID);
			if (similarity == null) {
				continue;
			}
			Comparison comparison = new Comparison(exampleID,
					loadNode(originalCodeXML, rowID + "_HR"), 
					loadNode(startCodeXML, rowID + "_" + exampleID), similarity);
			comparisons.add(comparison);
		}
		parser.close();
		
		return comparisons;
		
	}
	
	private static Node loadNode(String xml, String name) {
		Snapshot snapshot = Snapshot.parse(name, xml);
		return SimpleNodeBuilder.toTree(snapshot, true);
	}
	
	private static HashMap<Integer, Integer> loadRatings() 
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader("../data/csc200/all/example-ratings.csv"), 
				CSVFormat.DEFAULT.withHeader());
		
		HashMap<Integer, Integer> similarityMap = new HashMap<>();
		
		for (CSVRecord record : parser) {
			int exampleID = Integer.parseInt(record.get("ExampleID"));
			record.get("assignmentID");
			record.get("Semester");
			int similarity = Integer.parseInt(record.get("Similarity"));
			
			similarityMap.put(exampleID, similarity);
		}
		parser.close();
		
		return similarityMap;
	}
}
