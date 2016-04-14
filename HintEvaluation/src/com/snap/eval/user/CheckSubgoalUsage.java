package com.snap.eval.user;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.snap.eval.Assignment;
import com.snap.parser.DataRow;
import com.snap.parser.SolutionPath;

public class CheckSubgoalUsage {
	private final static String SUBGOAL_SELECTED = "Subgoal.selected";
	private final static String SUBGOAL_FINISHED = "Subgoal.finished";
	private final static String SUBGOAL_CHOOSE_DIFFERENT_OBJECTIVE = "Subgoal.chooseDifferentObjective";
	
	private final static List<String> SUBGOAL_MESSAGES = Arrays.asList(new String[] {
			SUBGOAL_SELECTED, SUBGOAL_FINISHED, SUBGOAL_CHOOSE_DIFFERENT_OBJECTIVE
	});
	
	public static void main(String[] args) {		
		for (Assignment assignment : Assignment.Spring2016.All) {
			HashMap<String,SolutionPath> submissions = assignment.load();
			
			// How many subgoals marked finished for each submission
			List<Integer> finishedList = new LinkedList<>();
			// Median # of seconds between subgoals finished 
			List<Integer> medianGapList = new LinkedList<>();
			
			for (String key : submissions.keySet()) {
				SolutionPath path = submissions.get(key);
				
				// Set of finished objectives 
				// (due to a bug, a subgoal may be marked finished multiple times)
				HashSet<String> finished = new HashSet<>();
				
				// Last time an objective was finished
				Date lastFinish = path.rows.get(0).timestamp;
				// Gaps between finishes
				List<Integer> gaps = new LinkedList<>();
				
				for (DataRow row : path) {
					// If this is a sugoal finish we haven't seen before ...
					if (SUBGOAL_FINISHED.equals(row.action)) {
						if (finished.add(row.data)) {
							// Calculate the gap
							Date finish = row.timestamp;
							gaps.add((int) (finish.getTime() - lastFinish.getTime()) / 1000);
							lastFinish = finish;
						}
					}
				}
				// Add the number of finished objectives
				finishedList.add(finished.size());

				// Add the median gap
				Collections.sort(gaps);
				if (gaps.size() > 0) {
					medianGapList.add(gaps.get(gaps.size() / 2));
				}
			}
			
			// Print
			Collections.sort(finishedList);
			Collections.reverse(finishedList);
			Collections.sort(medianGapList);
			Collections.reverse(medianGapList);
			
			System.out.println(assignment.name);
			System.out.println("Finished: " + finishedList);
			System.out.println("Median Gaps: " + medianGapList );
		}
	}

}
