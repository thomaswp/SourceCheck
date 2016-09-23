package com.snap.eval.user;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.snap.data.Snapshot;
import com.snap.eval.AutoGrader;
import com.snap.eval.AutoGrader.Grader;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;
import com.snap.parser.Assignment;
import com.snap.parser.AttemptAction;
import com.snap.parser.AssignmentAttempt;

public class CheckSubgoalUsage {
	private final static String SUBGOAL_SELECTED = "Subgoal.selected";
	private final static String SUBGOAL_FINISHED = "Subgoal.finished";
	private final static String SUBGOAL_CHOOSE_DIFFERENT_OBJECTIVE = "Subgoal.chooseDifferentObjective";
	
	private final static List<String> SUBGOAL_MESSAGES = Arrays.asList(new String[] {
			SUBGOAL_SELECTED, SUBGOAL_FINISHED, SUBGOAL_CHOOSE_DIFFERENT_OBJECTIVE
	});
	
	private final static HashMap<String, Grader[]> subgoalMap = new HashMap<>();
	static {
		subgoalMap.put("Welcome the Player", new Grader[] { new AutoGrader.WelcomePlayer() });
		subgoalMap.put("Greet the Player", new Grader[] { new AutoGrader.GreetByName() });
		subgoalMap.put("Store Secret Number", new Grader[] { new AutoGrader.StoreRandomNumber() });
		subgoalMap.put("Get a Guess", new Grader[] { new AutoGrader.GetGuess() });
		subgoalMap.put("Say if Correct", new Grader[] { new AutoGrader.ReportCorrect() });
		subgoalMap.put("Say if Too High\\/Low", new Grader[] { new AutoGrader.TooHigh(), new AutoGrader.TooLow() });
		subgoalMap.put("Repeat", new Grader[] { new AutoGrader.LoopUntilGuessed() });
	}
	
	public static void main(String[] args) throws IOException {		
		for (Assignment assignment : Assignment.Spring2016.All) {
			if (assignment != Assignment.Spring2016.GuessingGame1) continue;
			
			Map<String,AssignmentAttempt> submissions = assignment.load();
			
			File out = new File(assignment.dataDir + "/analysis/" + assignment.name + "/goals.csv");
			out.getParentFile().mkdirs();
			PrintStream ps = new PrintStream(out);
			CSVPrinter printer = new CSVPrinter(ps, CSVFormat.DEFAULT.withHeader("id", "finished", "satisfied", "satisfiedEnd", "grade", "gap"));
			
			for (String key : submissions.keySet()) {
				AssignmentAttempt path = submissions.get(key);
				
				// Set of finished objectives 
				// (due to a bug, a subgoal may be marked finished multiple times)
				HashSet<String> finished = new HashSet<>();
				
				// Last time an objective was finished
				Date lastFinish = path.rows.get(0).timestamp;
				// Gaps between finishes
				List<Integer> gaps = new LinkedList<>();
				
				Snapshot lastCode = null;
				
				int satisfied = 0;
				
				for (AttemptAction row : path) {
					
					if (row.snapshot != null) lastCode = row.snapshot;
					
					// If this is a sugoal finish we haven't seen before ...
					if (SUBGOAL_FINISHED.equals(row.action)) {
						if (finished.add(row.data)) {
							
							if (satisfied(lastCode, row.data)) {
								satisfied++;
							}
							
							// Calculate the gap
							Date finish = row.timestamp;
							gaps.add((int) (finish.getTime() - lastFinish.getTime()) / 1000);
							lastFinish = finish;
						}
					}
				}

				// Add the median gap
				Collections.sort(gaps);
				int gap = -1;
				if (gaps.size() > 0) {
					gap = gaps.get(gaps.size() / 2);
				}
				
				int satisfiedEnd = 0;
				for (String f : finished) {
					if (satisfied(lastCode, f)) satisfiedEnd++;
				}
				
				printer.printRecord(key, finished.size(), satisfied, satisfiedEnd, path.grade.average(), gap);
			}
			

			printer.close();
		}
	}
	
	private static boolean satisfied(Snapshot snapshot, String data) {
		Node node = SimpleNodeBuilder.toTree(snapshot, true);
		Grader[] graders = subgoalMap.get(data.substring(1, data.length() - 1));
		for (Grader grader : graders) {
			if (!grader.pass(node)) return false;
		}
		return true;
	}

}
