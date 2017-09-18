package edu.isnap.eval.agreement;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2017;

public class TutorEdits {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		readTutorEdits(Spring2017.instance);
	}

	public static void verifyHints(Dataset dataset) throws FileNotFoundException, IOException {
		readTutorEdits(dataset);
		// TODO
	}

	public static void readTutorEdits(Dataset dataset) throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(dataset.dataDir + "/handmade_hints.csv"),
				CSVFormat.DEFAULT.withHeader());
		for (CSVRecord record : parser) {
			System.out.println(record);
			// TODO
		}
		parser.close();
	}

	public static class TutorEdit {
		public final int id;
		public final String tutor;
		public final Node from, to;
		public final List<EditHint> edits;

		public TutorEdit(int id, String tutor, Node from, Node to) {
			this.id = id;
			this.tutor = tutor;
			this.from = from;
			this.to = to;
			edits = Agreement.findEdits(from, to);
		}

		public void verify() {
			Agreement.testEditConsistency(from, to, edits);
		}
	}
}
