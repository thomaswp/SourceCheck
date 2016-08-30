package com.snap.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import com.snap.data.Snapshot;
import com.snap.parser.Store.Mode;

public class Assignment {
	public final String dataDir, name;
	public final Date start, end;
	public final boolean hasIDs;
	public final boolean graded;
	
	public Assignment(String dataDir, String name, Date start, Date end, boolean hasIDs) {
		this(dataDir, name, start, end, hasIDs, false);
	}
	
	public Assignment(String dataDir, String name, Date start, Date end, 
			boolean hasIDs, boolean graded) {
		this.dataDir = dataDir;
		this.name = name;
		this.start = start;
		this.end = end;
		this.hasIDs = hasIDs;
		this.graded = graded;
	}
	
	public String analysisDir() {
		return dataDir + "/analysis/" + name;
	}
	
	public String unitTestDir() {
		return dataDir + "/unittests/" + name;
	}
	
	public Snapshot loadSolution() throws FileNotFoundException {
		return Snapshot.parse(new File(dataDir + "/solutions/", name + ".xml"));
	}

	public Snapshot loadTest(String name) throws FileNotFoundException {
		return Snapshot.parse(new File(dataDir + "/tests/", name + ".xml"));
	}

	public SolutionPath loadSubmission(String id, Mode mode, boolean snapshotsOnly) {
		try {
			return new SnapParser(dataDir, mode).parseSubmission(this, id, snapshotsOnly);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean ignore(String student) {
		return false;
	}
	
	@Override
	public String toString() {
		return dataDir + "/" + name;
	}
	
	public Map<String, SolutionPath> load() {
		return load(Mode.Use, false);
	}
	
	public Map<String, SolutionPath> load(Mode mode, boolean snapshotsOnly) {
		return new SnapParser(dataDir, mode).parseAssignment(name, snapshotsOnly, start, end);
	}
	
	public final static String BASE_DIR = "../data/csc200";
	
	// TODO: Find end times
	public static class Fall2015 {
		public final static Date start = new GregorianCalendar(2015, 7, 10).getTime();
		public final static String dataDir = BASE_DIR + "/fall2015";

		// Used this submission for testing, so not using it in evaluation
		public final static String GG1_SKIP = "3c3ce047-b408-417e-b556-f9406ac4c7a8.csv";
		
		public final static Assignment LightsCameraAction = new Assignment(dataDir, 
				"lightsCameraActionHW", start, null, false);
		public final static Assignment PolygonMaker = new Assignment(dataDir, 
				"polygonMakerLab", start, null, false);
		public final static Assignment Squiral = new Assignment(dataDir, 
				"squiralHW", start, null, false);
		public final static Assignment GuessingGame1 = new Assignment(dataDir, 
				"guess1Lab", start,  new GregorianCalendar(2015, 8, 18).getTime(), false, true) {
			@Override
			public boolean ignore(String id) {
				return GG1_SKIP.equals(id);
			};
		};
		public final static Assignment GuessingGame2 = new Assignment(dataDir, 
				"guess2HW", start, null, false);
		public final static Assignment GuessingGame3 = new Assignment(dataDir, 
				"guess3Lab", start, null, false);
		
		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};
	}
	
	public static class Spring2016 {
		public final static Date start = new GregorianCalendar(2016, 0, 1).getTime();
		public final static String dataDir = BASE_DIR + "/spring2016";
		
		public final static Assignment LightsCameraAction = new Assignment(dataDir, 
				"lightsCameraActionHW", start, new GregorianCalendar(2016, 0, 29).getTime(), true);
		public final static Assignment PolygonMaker = new Assignment(dataDir, 
				"polygonMakerLab", start, new GregorianCalendar(2016, 1, 2).getTime(), true);
		public final static Assignment Squiral = new Assignment(dataDir, 
				"squiralHW", start, new GregorianCalendar(2016, 1, 9).getTime(), true);
		public final static Assignment GuessingGame1 = new Assignment(dataDir, 
				"guess1Lab", start, new GregorianCalendar(2016, 1, 9).getTime(), true, true);
		public final static Assignment GuessingGame2 = new Assignment(dataDir, 
				"guess2HW", start, new GregorianCalendar(2016, 1, 16).getTime(), true);
		public final static Assignment GuessingGame3 = new Assignment(dataDir, 
				"guess3Lab", start, new GregorianCalendar(2016, 1, 23).getTime(), true);
		
		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};
	}
}
