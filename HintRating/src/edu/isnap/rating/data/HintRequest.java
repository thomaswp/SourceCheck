package edu.isnap.rating.data;

import edu.isnap.node.ASTSnapshot;

public class HintRequest {

	public final String id;
	public final String assignmentID;
	public final ASTSnapshot code;
	public final Trace history;

	public HintRequest(Trace trace) {
		this.id = trace.id;
		this.assignmentID = trace.assignmentID;
		this.code = trace.getFinalSnapshot();
		this.history = trace;
	}

}