package edu.isnap.rating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.ctd.util.map.MapFactory;

public class GoldStandard {

	private final HashMap<String, ListMap<String, TutorEdit>> map = new HashMap<>();
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

	public List<TutorEdit> getValidEdits(String assignment, String snapshotID) {
		return map.get(assignment).getList(snapshotID);
	}

	public GoldStandard(ListMap<String, ? extends TutorEdit> consensusEdits) {
		for (String assignment : consensusEdits.keySet()) {
			List<? extends TutorEdit> list = consensusEdits.get(assignment);
			ListMap<String, TutorEdit> snapshotMap = new ListMap<>(MapFactory.TreeMapFactory);
			list.forEach(edit -> snapshotMap.add(edit.requestID, edit));
			map.put(assignment, snapshotMap);

			Set<String> addedIDs = new HashSet<>();
			list.forEach(edit -> {
				if (addedIDs.add(edit.requestID)) {
					hintRequests.add(new HintRequest(edit.requestID, assignment, edit.from));
				}
			});
		}
	}

//	public void write(String rootDir) {
//		for (String assignment : map.keySet()) {
//
//		}
//	}
}