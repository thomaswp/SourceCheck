package com.snap.parser;

import java.io.Serializable;
import java.util.Date;

import com.snap.data.Snapshot;

public class DataRow implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public final Snapshot snapshot;
	public final Date timestamp;
	public final String action;
	
	@SuppressWarnings("unused")
	private DataRow() { 
		this(null, null, null);
	}
	
	public DataRow(Date timestamp, String action, String snapshotXML) {
		this.timestamp = timestamp;
		this.action = action;
		this.snapshot = Snapshot.parse(null, snapshotXML);
	}
	
	
}
