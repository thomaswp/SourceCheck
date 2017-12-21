package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.ctd.util.map.MapFactory;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.EditExtraction.Edit;
import edu.isnap.rating.TutorHint.Priority;
import edu.isnap.rating.TutorHint.Validity;

public class GoldStandard {

	private final HashMap<String, ListMap<String, TutorHint>> map = new HashMap<>();
	private final List<HintRequest> hintRequests = new ArrayList<>();

	public Set<String> getAssignmentIDs() {
		return map.keySet();
	}

	public List<HintRequest> getHintRequests() {
		return Collections.unmodifiableList(hintRequests);
	}

	public Set<String> getRequestIDs(String assignmentID) {
		return map.get(assignmentID).keySet();
	}

	public List<TutorHint> getValidEdits(String assignment, String snapshotID) {
		return map.get(assignment).getList(snapshotID);
	}

	public GoldStandard(ListMap<String, ? extends TutorHint> hints) {
		for (String assignment : hints.keySet()) {
			List<? extends TutorHint> list = hints.get(assignment);
			ListMap<String, TutorHint> hintMap = new ListMap<>(MapFactory.TreeMapFactory);
			list.forEach(hint -> hintMap.add(hint.requestID, hint));
			list.forEach(hint -> {
				Set<Edit> editsTED = EditExtraction.extractEditsUsingTED(hint.from, hint.to);
				Set<Edit> editsIDs = EditExtraction.extractEditsUsingIDs(hint.from, hint.to);
				if (!editsTED.equals(editsIDs)) {
					System.out.println(hint.toDiff(RatingConfig.Snap));
					Set<Edit> ted = new HashSet<>(editsTED),
							id = new HashSet<>(editsIDs),
							both = new HashSet<>(editsIDs);
					ted.removeAll(editsIDs);
					id.removeAll(editsTED);
					both.retainAll(editsTED);

					System.out.println("Both:");
					both.forEach(System.out::println);
					System.out.println("IDs Only:");
					id.forEach(System.out::println);
					System.out.println("TED Only:");
					ted.forEach(System.out::println);

					System.out.println("------------");
				}
			});
			map.put(assignment, hintMap);

			Set<String> addedIDs = new HashSet<>();
			list.forEach(edit -> {
				if (addedIDs.add(edit.requestID)) {
					hintRequests.add(new HintRequest(edit.requestID, assignment, edit.from));
				}
			});
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
					hintID, requestID, "consensus", assignmentID, lastFrom, to);
			hint.validity = validity;
			hint.priority = priority;
			hints.add(assignmentID, hint);
		}
		parser.close();
		return new GoldStandard(hints);

	}
}