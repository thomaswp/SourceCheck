package com.snap.eval.user;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

import com.snap.parser.SnapParser;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store.Mode;

public class Assignment {
	public final String dataDir, name;
	public final Date start, end;
	
	public Assignment(String dataDir, String name, Date start, Date end) {
		this.dataDir = dataDir;
		this.name = name;
		this.start = start;
		this.end = end;
	}
	
	public HashMap<String, SolutionPath> load() {
		return load(Mode.Use, false);
	}
	
	public HashMap<String, SolutionPath> load(Mode mode, boolean snapshotsOnly) {
		return new SnapParser(dataDir, mode).parseAssignment(name, snapshotsOnly, start, end);
	}
	
	public static class Spring2016 {
		private final static Date start = new GregorianCalendar(2016, 1, 1).getTime();
		private final static String dataDir = "../data/csc200/spring2016";
		
		public final static Assignment LightsCameraAction = new Assignment(dataDir, 
				"lightsCameraActionHW", start, new GregorianCalendar(2015, 1, 29).getTime());
		public final static Assignment PolygonMaker = new Assignment(dataDir, 
				"polygonMakerLab", start, new GregorianCalendar(2016, 2, 2).getTime());
		public final static Assignment Squiral = new Assignment(dataDir, 
				"squiralHW", start, new GregorianCalendar(2016, 2, 9).getTime());
		public final static Assignment GuessingGame1 = new Assignment(dataDir, 
				"guess1Lab", start, new GregorianCalendar(2016, 2, 9).getTime());
		public final static Assignment GuessingGame2 = new Assignment(dataDir, 
				"guess2HW", start, new GregorianCalendar(2016, 2, 16).getTime());
		public final static Assignment GuessingGame3 = new Assignment(dataDir, 
				"guess3Lab", start, new GregorianCalendar(2016, 2, 23).getTime());
		
		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};
	}
}
