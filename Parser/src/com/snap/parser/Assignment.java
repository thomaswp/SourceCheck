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

	private Assignment(String dataDir, String name, Date start, Date end, boolean hasIDs) {
		this(dataDir, name, start, end, hasIDs, false);
	}

	private Assignment(String dataDir, String name, Date start, Date end,
			boolean hasIDs, boolean graded) {
		this.dataDir = dataDir;
		this.name = name;
		this.start = start;
		this.end = end;
		this.hasIDs = hasIDs;
		this.graded = graded;
	}

	public String analysisDir() {
		return dir("analysis");
	}

	public String unitTestDir() {
		return dir("unittests");
	}

	public String submittedDir() {
		return dir("submitted");
	}

	public String gradesFile() {
		return dir("grades") + ".csv";
	}

	public String parsedDir() {
		return dir("parsed");
	}

	private String dir(String folderName) {
		return dataDir + "/" + folderName + "/" + name;
	}

	public Snapshot loadSolution() throws FileNotFoundException {
		return Snapshot.parse(new File(dataDir + "/solutions/", name + ".xml"));
	}

	public Snapshot loadTest(String name) throws FileNotFoundException {
		return Snapshot.parse(new File(dataDir + "/tests/", name + ".xml"));
	}

	public AssignmentAttempt loadSubmission(String id, Mode mode, boolean snapshotsOnly) {
		try {
			return new SnapParser(this, mode).parseSubmission(id, snapshotsOnly);
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

	public Map<String, AssignmentAttempt> load() {
		return load(Mode.Use, false);
	}

	public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly) {
		return new SnapParser(this, mode).parseAssignment(snapshotsOnly);
	}

	public final static String BASE_DIR = "../data/csc200";

	// Note: end dates are generally 2 days past last class due date

	public static class Fall2015 {
		public final static Date start = date(2015, 8, 10);
		public final static String dataDir = BASE_DIR + "/fall2015";
		public final static String dataFile = dataDir + ".csv";

		// Used this submission for testing, so not using it in evaluation
		// For the comparison 2015/2016 study we should keep it, though
		public final static String GG1_SKIP = "3c3ce047-b408-417e-b556-f9406ac4c7a8.csv";

		// Note: the first three assignments were not recorded in Fall 2015
//		public final static Assignment LightsCameraAction = new Assignment(dataDir,
//				"lightsCameraActionHW", start, date(2016, 9, 4), false);
//		public final static Assignment PolygonMaker = new Assignment(dataDir,
//				"polygonMakerLab", start, date(2015, 9, 4), false);
//		public final static Assignment Squiral = new Assignment(dataDir,
//				"squiralHW", start, date(2015, 9, 13), false);
		public final static Assignment GuessingGame1 = new Assignment(dataDir,
				"guess1Lab", start, date(2015, 9, 18), false, true);
		public final static Assignment GuessingGame2 = new Assignment(dataDir,
				"guess2HW", start, date(2015, 9, 25), false) {
			@Override
			public boolean ignore(String student) {
				// Actually work on guess3Lab with the same ID as a guess2HW submission
				return "10e87347-75ca-4d07-9b65-138c47332aca".equals(student);
			}
		};
		public final static Assignment GuessingGame3 = new Assignment(dataDir,
				"guess3Lab", start, date(2015, 10, 2), false);

		public final static Assignment[] All = {
//			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};
	}

	public static class Spring2016 {
		public final static Date start = date(2016, 1, 1);
		public final static String dataDir = BASE_DIR + "/spring2016";
		public final static String dataFile = dataDir + ".csv";

		public final static Assignment LightsCameraAction = new Assignment(dataDir,
				"lightsCameraActionHW", start, date(2016, 1, 29), true);
		public final static Assignment PolygonMaker = new Assignment(dataDir,
				"polygonMakerLab", start, date(2016, 2, 2), true);
		public final static Assignment Squiral = new Assignment(dataDir,
				"squiralHW", start, date(2016, 2, 9), true);
		public final static Assignment GuessingGame1 = new Assignment(dataDir,
				"guess1Lab", start, date(2016, 2, 9), true, true);
		public final static Assignment GuessingGame2 = new Assignment(dataDir,
				"guess2HW", start, date(2016, 2, 16), true);
		public final static Assignment GuessingGame3 = new Assignment(dataDir,
				"guess3Lab", start, date(2016, 2, 23), true);

		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};
	}

	public static class Fall2016 {
		public final static Date start = date(2015, 8, 17);
		public final static String dataDir = BASE_DIR + "/fall2016";
		public final static String dataFile = dataDir + ".csv";

		public final static Assignment LightsCameraAction = new Assignment(dataDir,
				"lightsCameraActionHW", start, date(2016, 9, 2), true);
		public final static Assignment PolygonMaker = new Assignment(dataDir,
				"polygonMakerLab", start, date(2016, 9, 2), true);
		public final static Assignment Squiral = new Assignment(dataDir,
				"squiralHW", start, date(2016, 9, 11), true);
		public final static Assignment GuessingGame1 = new Assignment(dataDir,
				"guess1Lab", start, date(2016, 9, 16), true, true);
		public final static Assignment GuessingGame2 = new Assignment(dataDir,
				"guess2HW", start, date(2016, 9, 23), true);
		public final static Assignment GuessingGame3 = new Assignment(dataDir,
				"guess3Lab", start, date(2016, 9, 30), true);

		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};
	}

	public static Date date(int year, int month, int day) {
		// NOTE: GregorianCalendar months are 0-based, thus the 'month - 1'
		return new GregorianCalendar(year, month - 1, day).getTime();
	}
}
