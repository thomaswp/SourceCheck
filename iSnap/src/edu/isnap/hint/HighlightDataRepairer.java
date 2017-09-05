package edu.isnap.hint;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

/**
 * In Spring 2017 there was a in the javascript logger that cause the HintProvider.ProcessHints
 * message not to include any data. This script is used to recalculate the hints that would have
 * been provided. It saves these hints into .json files in the hintRepair directory, and these
 * are loaded by the SnapParse if present. Note that this script must be run with the version of
 * hint generation that was used in the data collection, since the most recent version may provide
 * different hints. It also need to be given the same hintDataset for calculating hints.
 */
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

		// If this ever happens with another hint generator, it will need to be used here instead
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
				writer.write(actionObject.toString(4));
				writer.close();
			}
		}
	}

	public static void testRepair(Assignment assignment) {
		for (AssignmentAttempt attempt : assignment.load(Mode.Use, false).values()) {
			for (AttemptAction action : attempt) {
				if (action.lastSnapshot == null) continue;
				if (AttemptAction.HINT_PROCESS_HINTS.equals(action.message) &&
						action.data.equals("\"\"")) {
					System.out.println("Failed to repair: " + attempt.id + "/" + action.id);
				}
			}
		}
	}
}
