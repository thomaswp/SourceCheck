package edu.isnap.hint;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintHighlighter.EditHint;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

public class HighlightDataRepairer {
	public static void repair(Assignment assignment, Dataset hintDataset, double minGrade)
			throws IOException {
		Assignment hintAssignment = null;
		for (Assignment hAssignment : hintDataset.all()) {
			if (hAssignment.name.equals(assignment.name)) {
				hintAssignment = hAssignment;
				break;
			}
		}
		if (hintAssignment == null) {
			System.out.println("No matching assignment found in dataset.");
			return;
		}

		HintHighlighter highlighter = new SnapHintBuilder(hintAssignment).buildGenerator(
				Mode.Ignore, minGrade).hintHighlighter();

		for (AssignmentAttempt attempt : assignment.load(Mode.Use, false, false).values()) {
			JSONObject actionObject = new JSONObject();
			int count = 0;
			boolean assignmentSwitch = false;
			for (AttemptAction action : attempt) {
				assignmentSwitch |= AttemptAction.ASSIGNMENT_SET_ID_FROM.equals(action.message);
				if (AttemptAction.HINT_PROCESS_HINTS.equals(action.message)) {
					if (action.data.equals("\"\"")) {
						if (action.lastSnapshot == null) {
							// If the assignment was switched before this error, it's just missing
							// data because it didn't used to record a snapshot when this happens
							if (!assignmentSwitch) {
								System.err.println(
										"Hint with no snapshot: " + attempt.id + "/" + action.id);
							}
							continue;
						}
						List<EditHint> hints = highlighter.highlight(
								SimpleNodeBuilder.toTree(action.lastSnapshot, true));
						JSONArray hintArray = HintJSON.hintArray(hints);
						actionObject.put(String.valueOf(action.id), hintArray);
						count++;
					}
				}
			}
			if (count > 0) {
				System.out.println("Processed: " + attempt.id);
				File file = new File(
						assignment.hintRepairDir() + "/" + attempt.id + ".json");
				file.getParentFile().mkdirs();
				BufferedWriter writer = new BufferedWriter(new FileWriter(file));
				writer.write(actionObject.toString());
				writer.close();
			}
		}
	}
}
