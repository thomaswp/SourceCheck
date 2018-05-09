package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.ctd.util.map.MapFactory;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.TutorHint.Priority;
import edu.isnap.rating.TutorHint.Validity;

public class GoldStandard {

	private final HashMap<String, ListMap<String, TutorHint>> map = new HashMap<>();

	public Set<String> getAssignmentIDs() {
		return map.keySet();
	}

	public Set<String> getRequestIDs(String assignmentID) {
		return map.get(assignmentID).keySet();
	}

	public List<TutorHint> getValidHints(String assignmentID, String requestID) {
		return map.get(assignmentID).getList(requestID);
	}

	public ASTNode getHintRequestNode(String assignment, String requestID) {
		List<TutorHint> edits = getValidHints(assignment, requestID);
		if (edits == null || edits.isEmpty()) return null;
		return edits.get(0).from;
	}

	public GoldStandard(ListMap<String, ? extends TutorHint> hints) {
		for (String assignment : hints.keySet()) {
			List<? extends TutorHint> list = hints.get(assignment);
			ListMap<String, TutorHint> hintMap = new ListMap<>(MapFactory.TreeMapFactory);
			list.forEach(hint -> hintMap.add(hint.requestID, hint));
			map.put(assignment, hintMap);
		}
	}

	public static GoldStandard merge(GoldStandard... standards) {
		ListMap<String, TutorHint> allHints = new ListMap<>();
		for (GoldStandard standard : standards) {
			standard.map.values().stream()
			.flatMap(map -> map.values().stream())
			.flatMap(list -> list.stream())
			.forEach(hint -> allHints.add(hint.assignmentID, hint));
		}
		return new GoldStandard(allHints);
	}

	public void writeSpreadsheet(String path) throws FileNotFoundException, IOException {
		Spreadsheet spreadsheet = new Spreadsheet();
		for (String assignmentID : map.keySet()) {
			ListMap<String, TutorHint> hintMap = map.get(assignmentID);
			for (String requestID : hintMap.keySet()) {
				List<TutorHint> hints = hintMap.get(requestID);
				for (int i = 0; i < hints.size(); i++) {
					TutorHint hint = hints.get(i);
					String fromJSON = i == 0 ? hint.from.toJSON().toString() : "";
					spreadsheet.newRow();
					spreadsheet.put("assignmentID", assignmentID);
					spreadsheet.put("requestID", requestID);
					spreadsheet.put("year", hint.year);
					spreadsheet.put("hintID", hint.hintID);
					spreadsheet.put("validity", hint.validity.value);
					spreadsheet.put("priority", hint.priority == null ? "" : hint.priority.value);
					spreadsheet.put("from", fromJSON);
					spreadsheet.put("to", hint.to.toJSON().toString());
				}
			}
		}
		spreadsheet.write(path);
	}

	public static GoldStandard parseSpreadsheet(String path)
			throws FileNotFoundException, IOException {
		ListMap<String, TutorHint> hints = new ListMap<>();
		CSVParser parser = new CSVParser(new FileReader(path), CSVFormat.DEFAULT.withHeader());
		ASTNode lastFrom = null;
		for (CSVRecord record : parser) {
			String assignmentID = record.get("assignmentID");
			String requestID = record.get("requestID");
			String year = record.get("year");
			int hintID = Integer.parseInt(record.get("hintID"));
			Validity validity = Validity.fromInt(Integer.parseInt(record.get("validity")));
			String priorityString = record.get("priority");
			Priority priority = priorityString.isEmpty() ?
					null : Priority.fromInt(Integer.parseInt(priorityString));

			String fromSource = record.get("from");
			if (!fromSource.isEmpty()) {
				lastFrom = ASTNode.parse(fromSource);
			}
			ASTNode to = ASTNode.parse(record.get("to"));

			TutorHint hint = new TutorHint(
					hintID, requestID, "consensus", assignmentID, year, lastFrom, to);
			hint.validity = validity;
			hint.priority = priority;
			hints.add(assignmentID, hint);
		}
		parser.close();
		return new GoldStandard(hints);

	}

	public GoldStandard filterForAssignment(String assignmentID) {
		ListMap<String, TutorHint> hints = new ListMap<>();
		map.get(assignmentID).values()
		.stream().flatMap(list -> list.stream())
		.forEach(hint -> hints.add(assignmentID, hint));
		return new GoldStandard(hints);
	}

	public void printAllRequestNodes(RatingConfig config) {
		for (String assignmentID : getAssignmentIDs()) {
			System.out.println(" ============= " + assignmentID + " ============= ");
			for (String requestID : getRequestIDs(assignmentID)) {
				ASTNode from = getHintRequestNode(assignmentID, requestID);
				if (from == null) continue;
				System.out.println(requestID);
				System.out.println(from.prettyPrint(true, config));
				System.out.println("----------------");
			}
		}
	}
}