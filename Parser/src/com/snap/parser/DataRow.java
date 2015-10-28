package com.snap.parser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.snap.data.Snapshot;

public class DataRow implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final static DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	
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
		String name;
		synchronized (format) {
			name = timestamp == null ? null : format.format(timestamp);
		}
		this.snapshot = Snapshot.parse(name, snapshotXML);
	}
	
	
}
