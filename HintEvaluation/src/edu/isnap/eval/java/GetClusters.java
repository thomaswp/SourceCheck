package edu.isnap.eval.java;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import com.opencsv.CSVWriter;

import clustering.SolutionClusterer;
import edu.isnap.node.JavaNode;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Annotations;
import net.sf.javaml.core.Instance;

public class GetClusters {

	public static void main(String[] args) throws IOException {
		int task = 1;
		// String dataDir = "../data/F19_Project_3_2/task" + task + "/";
		String dataDir = "../data/S20_3.3_OPE_Grading_Anon/3.3_OPE_Submissions-anonymized/"; // The path to the folder containing different students' source files
		// String separator = "@andrew.cmu.edu_social-network_p32-task" + task + "_";
		String separator = "@andrew.cmu.edu_data-consistency-ope_consistency-ope-task_"; // You may not need this. This is useful when the names of folders for different students share separator string.
		// String[] assignments = {"ProfileServlet", "FollowerServlet", "HomepageServlet", "TimelineServlet"};
		String[] assignments = {"BankUserConcurrentGet", "BankUserConcurrentPut", "BankUserMultiThreaded", "BankUserStrongConsistency"};
		String assignment = assignments[task - 1]; // The name of the source file
		String sourcePath = "/src/main/java/Project_OMP/BankUserSystem/"; // The path to the source file folder for each student
		String outFile = dataDir + "cluster_info_task" + task + ".csv";

		HashMap<String, LinkedHashMap<String, JavaNode>> attempts = JavaImport.loadAssignment(
				dataDir + "input_task" + task + ".csv", true, assignment, dataDir, separator, sourcePath);

		List<Node> correct = new ArrayList<>();
		LinkedHashMap<Integer, JavaNode> annotated = new LinkedHashMap<>();
		for (String studentID : attempts.keySet()) {
			List<String> timestamps = new ArrayList<>(attempts.get(studentID).keySet());
			Collections.sort(timestamps);
			JavaNode node = attempts.get(studentID).get(timestamps.get(timestamps.size() - 1));
			if (!node.readOnlyAnnotations().equals(Annotations.EMPTY)) {
				annotated.put(node.cluster.get(), node);
			}

			// If it was correct, then add it to the subset
			if (node.correct.orElse(false)) {
				correct.add(node);
			}
		}

		SolutionClusterer clusterer = new SolutionClusterer(correct, assignment);
		HashMap<String, HashMap<String, Instance>> solutionMaps = clusterer.clusterSolutions();
		List<String> students = new ArrayList<String>(solutionMaps.keySet());
		Collections.sort(students);

		// Open a csv file
		File clusterCSV = new File(outFile);
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

		}

		// closing writer connection
		writer.close();
	}

}
