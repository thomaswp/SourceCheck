package edu.isnap.rating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.util.map.ListMap;

public class GoldStandard {

	private final HashMap<String, ListMap<Integer, TutorEdit>> map = new HashMap<>();
	private final List<HintRequest> hintRequests = new ArrayList<>();

	public Set<String> getAssignments() {
		return map.keySet();
	}

	public List<HintRequest> getHintRequests() {
		return Collections.unmodifiableList(hintRequests);
	}

	public Set<Integer> getSnapshotIDs(String assignment) {
		return map.get(assignment).keySet();
	}

	public List<TutorEdit> getValidEdits(String assignment, int snapshotID) {
		return map.get(assignment).getList(snapshotID);
	}

	public GoldStandard(ListMap<String, ? extends TutorEdit> consensusEdits) {
		for (String assignment : consensusEdits.keySet()) {
			List<? extends TutorEdit> list = consensusEdits.get(assignment);
			ListMap<Integer, TutorEdit> snapshotMap = new ListMap<>();
			list.forEach(edit -> snapshotMap.add(edit.requestID, edit));
			map.put(assignment, snapshotMap);

			Set<Integer> addedIDs = new HashSet<>();
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