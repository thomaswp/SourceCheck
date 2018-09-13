package edu.isnap.eval.mturk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.MTurk2018;
import edu.isnap.eval.AutoGrader.Grader;
import edu.isnap.eval.PolygonAutoGrader;
import edu.isnap.eval.TriangleSeriesAutoGrader;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.Store.Mode;

public class MTurkProcess {
	public static void main(String[] args) throws FileNotFoundException, IOException {
		processDataset(MTurk2018.instance);
	}

	static void processDataset(Dataset dataset) throws FileNotFoundException, IOException {
		Spreadsheet attempts = new Spreadsheet();
		Spreadsheet actions = new Spreadsheet();
		for (Assignment assignment : dataset.all()) {
			System.out.println("Processing: " + assignment);
			attempts.setHeader("assignmentID", assignment.name);
			actions.setHeader("assignmentID", assignment.name);

			Grader[] graders = null;
			if ("polygonMakerSimple".equals(assignment.name)) {
				graders = PolygonAutoGrader.PolygonGraders;
			} else if ("drawTriangles".equals(assignment.name)) {
				graders = TriangleSeriesAutoGrader.PolygonTriangleSeriesGraders;
			}
			Map<String, AssignmentAttempt> data = assignment.load(Mode.Use, false);
			System.out.println("Number of Submissions: " + data.size());
			Object[] arr_data = data.values().toArray();
			for (int i = 0; i < arr_data.length; i++) {
				AssignmentAttempt attempt = (AssignmentAttempt) arr_data[i];
				processAttempt(attempt, graders, attempts, actions);
			}
//			for (AssignmentAttempt attempt : data.values()) {
//				processAttempt(attempt, graders, attempts, actions);
//			}
		}
		attempts.write(dataset.analysisDir() + "/attempts.csv");
		actions.write(dataset.analysisDir() + "/actions.csv");
	}

	static void processAttempt(AssignmentAttempt attempt, Grader[] graders,
			Spreadsheet attempts, Spreadsheet actions) {
		actions.setHeader("userID", attempt.userID());
		actions.setHeader("projectID", attempt.id);

		int errors = 0;
		int idleTime = 0, activeTime = 0;
		int textHints = 0, codeHints = 0, reflects = 0, noHints = 0;
		int firstEditTime = 0, midSurveyTime = 0, firstNoHints = 0;
		int[] objectiveTimes = new int[graders.length];
		int objs = 0;

		long start = attempt.rows.get(0).timestamp.getTime();
		long lastEdit = start;

		Node lastCode = null;

		for (AttemptAction row : attempt.rows) {
			long time = row.timestamp.getTime();
			int relTime = (int) (time - start);

			if (row.snapshot != null) {
				int duration = (int) (time - lastEdit);
				if (duration > 3 * 60 * 1000) {
					idleTime += duration;
				} else {
					activeTime += duration;
				}
				if (firstEditTime == 0) firstEditTime = relTime;
				lastEdit = time;

				Node node = lastCode = SimpleNodeBuilder.toTree(row.snapshot, false);
				for (int i = 0; i < graders.length; i++) {
					if (objectiveTimes[i] > 0) continue;
					if (graders[i].pass(node)) {
						objectiveTimes[i] = relTime;
						objs++;
					}
				}
			}

			switch (row.message) {
			case AttemptAction.ERROR: errors++; break;
			case AttemptAction.PROACTIVE_SEE_HINT:
				JSONObject data = new JSONObject(row.data);
				boolean codeHint = data.getBoolean("codeHintNext");
				boolean textHint = data.getBoolean("textHintNext");
				boolean reflect = data.getBoolean("selfExplainNext");
				String eventID = data.getString("eventID");
				if (codeHint) codeHints++;
				if (textHint) textHints++;
				if (reflect) reflects++;

				Node node = SimpleNodeBuilder.toTree(row.lastSnapshot, false);

				actions.newRow();
				actions.put("eventID", eventID);
				actions.put("codeHint", codeHint);
				actions.put("textHint", textHint);
				actions.put("reflect", reflect);
				actions.put("time", relTime);
				actions.put("treeSize", node.treeSize());
				actions.put("objs", objs);

				break;
			case AttemptAction.PROACTIVE_NO_HINT:
				noHints++;
				if (firstNoHints == 0) firstNoHints = (int) (time - start);
				break;
			}

			if (AttemptAction.PROACTIVE_MIDSURVEY.equals(row.message)) {
				midSurveyTime = relTime;
				break;
			}
		}
//		if (lastCode != null) {
//			System.out.println(attempt.id);
//			System.out.println(lastCode.prettyPrint(true));
//			for (Grader grader : graders) {
//				System.out.println(grader.name() + ": " + grader.pass(lastCode));
//			}
//			System.out.println();
//		}

		attempts.newRow();
		attempts.put("userID", attempt.userID());
		attempts.put("projectID", attempt.id);
		attempts.put("errors", errors);
		attempts.put("idleTime", idleTime);
		attempts.put("activeTime", activeTime);
		attempts.put("codeHints", codeHints);
		attempts.put("textHints", textHints);
		attempts.put("reflects", reflects);
		attempts.put("noHints", noHints);
		attempts.put("firstNoHints", firstNoHints == 0 ? null : firstNoHints);
		attempts.put("firstEditTime", firstEditTime == 0 ? null : firstEditTime);
		attempts.put("midSurveyTime", midSurveyTime == 0 ? null : midSurveyTime);
		attempts.put("objs", objs);
		for (int i = 0; i < objectiveTimes.length; i++) {
			attempts.put("obj" + i, objectiveTimes[i] == 0 ? null : objectiveTimes[i]);
		}
		attempts.put("lastCode", lastCode.prettyPrint(true));
	}
}
