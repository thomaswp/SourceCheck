package edu.isnap.rating;

import edu.isnap.ctd.graph.ASTNode;

public class HintRequest {
	public final String id;
	public final String assignmentID;
	public final ASTNode code;

	public HintRequest(String id, String assignmentID, ASTNode code) {
		this.id = id;
		this.assignmentID = assignmentID;
		this.code = code;
	}
}