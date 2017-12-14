package edu.isnap.rating;

import edu.isnap.ctd.graph.ASTNode;

// TODO: Merge with or differentiate from other HintRequest class
public class HintRequest {
	public final int id;
	public final String assignmentID;
	public final ASTNode code;

	public HintRequest(int id, String assignmentID, ASTNode code) {
		this.id = id;
		this.assignmentID = assignmentID;
		this.code = code;
	}
}