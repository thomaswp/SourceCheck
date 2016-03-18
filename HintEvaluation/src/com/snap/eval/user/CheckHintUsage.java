package com.snap.eval.user;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.parser.DataRow;
import com.snap.parser.SolutionPath;

public class CheckHintUsage {
	
	private final static String SHOW_SCRIPT_HINT = "SnapDisplay.showScriptHint";
	private final static String SHOW_BLOCK_HINT = "SnapDisplay.showBlockHint";
	private final static String SHOW_STRUCTURE_HINT = "SnapDisplay.showStructureHint";
	
	private final static String HINT_DIALOG_DONE = "HintDialogBox.done";
	
	private final static List<String> SHOW_HINT_MESSAGES = Arrays.asList(new String[] {
			SHOW_SCRIPT_HINT, SHOW_BLOCK_HINT, SHOW_STRUCTURE_HINT
	});
	
	public static void main(String[] args) {
		
		// Get the name-path pairs of all projects we logged
		HashMap<String, SolutionPath> guessingGame = Assignment.Spring2016.GuessingGame1.load();
		
		int nHints = 0, nThumbsUp = 0, nThumbsDown = 0, nHintsTaken = 0;
		
		// Iterate over all submissions
		for (String submission : guessingGame.keySet()) {
			SolutionPath path = guessingGame.get(submission);
			
			// Ignore any that weren't exported (and thus couldn't have been submitted)
			if (!path.exported) continue;
			
			Snapshot code = null;
			for (int i = 0; i < path.size(); i++) {
				DataRow row = path.rows.get(i);
				
				// If this row had an update to the code, update it
				if (row.snapshot != null) code = row.snapshot;
				
				// Check if this action was showing a hint
				String action = row.action;
				if (SHOW_HINT_MESSAGES.contains(action)) {
					nHints++;
					
					JSONObject data = new JSONObject(row.data);
					Node node = SimpleNodeBuilder.toTree(code, true);
					
					Node parent = findParent(node, data);
					if (parent == null) {
						System.out.println(node.prettyPrint());
						System.out.println(data);
						findParent(node, data);
						throw new RuntimeException("Parent shouldn't be null :/");
					}
					
					JSONArray toArray = data.getJSONArray("to");
					String[] to = new String[toArray.length()];
					for (int j = 0; j < to.length; j++) to[j] = toArray.getString(j); 
					Node hintOutcome = VectorHint.applyHint(parent, to);
					
					System.out.println(parent + "\n->" + hintOutcome + "\n");
										
					int steps = 0;
					for (int j = i; j < path.size(); j++) {
						DataRow nextRow = path.rows.get(j);
						Snapshot nextCode = nextRow.snapshot;
						if (nextCode == null) continue;
						steps++;
						
						// If we've looked more than n (10) steps in the future, give up
						if (steps > 10) break;
						
						Node nextNode = SimpleNodeBuilder.toTree(nextCode, true);
						Node nextParent = findParent(nextNode, data);
						if (nextParent == null) continue;

						if (nextParent.equals(hintOutcome)) {
							nHintsTaken++;
							break;
						}
					}
				}
				
				// Check if this action was dismissing a hint
				if (HINT_DIALOG_DONE.equals(action)) {
					// TODO: inspect good and bad hints
					if (row.data.equals("[\"up\"]")) {
						nThumbsUp++;
					} else if (row.data.equals("[\"down\"]")) {
						nThumbsDown++;
					}
				}
			}
		}
		
		System.out.println("Total Hints Selected: " + nHints);
		System.out.println("Thumbs Up: " + nThumbsUp + "/" + nHints);
		System.out.println("Thumbs Down: " + nThumbsDown + "/" + nHints);
		System.out.println("Hints Taken: " + nHintsTaken + "/" + nHints);
		
	}
	
	private static Object getValue(JSONObject obj, String key) {
		if (!obj.has(key) || obj.isNull(key)) return null;
		return obj.get(key);
	}
	
	public static Node findParent(Node root, JSONObject data) {
		String[] ids = new String[] {
				"parentID", "rootID", "rootType"
		};
		
		for (String id : ids) {
			Object parentID = getValue(data, id);
			if (parentID != null) {
				return root.searchForNodeWithID(parentID);
			}	
		}
		
		return null;
	}
}
