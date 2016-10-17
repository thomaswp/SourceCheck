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
	public final Dataset dataset;
	public final String dataDir, name;
	public final Date start, end;
	public final boolean hasIDs;
	public final boolean graded;
	public final Assignment prequel;
	public final Assignment None;

	private Assignment(Dataset dataset, String name, Date end, boolean hasIDs) {
		this(dataset, name, end, hasIDs, false, null);
	}

	private Assignment(Dataset dataset, String name, Date end, boolean hasIDs, boolean graded,
			Assignment prequel) {
		this.dataset = dataset;
		this.dataDir = dataset.dataDir;
		this.name = name;
		this.start = dataset.start;
		this.end = end;
		this.hasIDs = hasIDs;
		this.graded = graded;
		this.prequel = prequel;
		this.None = name.equals("none") ? null : new Assignment(dataset, "none", end, hasIDs);
	}

	public HintConfig config() {
		return new HintConfig();
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

	public String dir(String folderName) {
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

	/** Override to ignore certain known bad attempts. */
	public boolean ignore(String attemptID) {
		return false;
	}

	/** Override to mark certain attempts as being submitted under a different assignment folder. */
	public Assignment getLocationAssignment(String attemptID) {
		return this;
	}

	/**
	 * Override to manually define the submitted row ID for attempts that change minimally from that
	 * row to submission.
	 */
	public Integer getSubmittedRow(String attemptID) {
		return null;
	}

	@Override
	public String toString() {
		return dataDir + "/" + name;
	}

	public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly) {
		return load(mode, snapshotsOnly, true);
	}

	public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly,
			boolean addMetadata) {
		return new SnapParser(this, mode).parseAssignment(snapshotsOnly, addMetadata);
	}

	public final static String BASE_DIR = "../data/csc200";

	public static abstract class Dataset {
		public final Date start;
		public final String dataDir, dataFile;

		public abstract Assignment[] all();

		private Dataset(Date start, String dataDir) {
			this.start = start;
			this.dataDir = dataDir;
			this.dataFile = dataDir + ".csv";
		}

	}

	// Note: end dates are generally 2 days past last class due date

	/** Demo dataset from http://go.ncsu.edu/isnap - publicly sharable data */
	public static class Demo extends Dataset {

		public final static Demo instance = new Demo();
		public final static Date start = date(2015, 8, 10);
		public final static String dataDir = BASE_DIR + "/demo";
		public final static String dataFile = dataDir + ".csv";

		public final static Assignment LightsCameraAction = new Assignment(instance,
				"lightsCameraActionHW", null, true);
		public final static Assignment PolygonMaker = new Assignment(instance,
				"polygonMakerLab", null, true);
		public final static Assignment Squiral = new Assignment(instance,
				"squiralHW", null, true);
		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", null, true, true, null);
		public final static Assignment GuessingGame2 = new Assignment(instance,
				"guess2HW", null, true, false, GuessingGame1);
		public final static Assignment GuessingGame3 = new Assignment(instance,
				"guess3Lab", null, true);

		public final static Assignment[] All = {
				LightsCameraAction, PolygonMaker, Squiral,
				GuessingGame1, GuessingGame2, GuessingGame3
			};

			private Demo() {
				super(start, dataDir);
			}

			@Override
			public Assignment[] all() {
				return All;
			}
	}

	public static class Fall2015 extends Dataset {

		public final static Fall2015 instance = new Fall2015();
		public final static Date start = date(2015, 8, 10);
		public final static String dataDir = BASE_DIR + "/fall2015";
		public final static String dataFile = dataDir + ".csv";

		// Used this submission for testing, so not using it in evaluation
		// For the comparison 2015/2016 study we should keep it, though
		public final static String GG1_SKIP = "3c3ce047-b408-417e-b556-f9406ac4c7a8";


		// Note: the first three assignments were not recorded in Fall 2015
//		public final static Assignment LightsCameraAction = new Assignment(dataDir,
//				"lightsCameraActionHW", start, date(2016, 9, 4), false);
//		public final static Assignment PolygonMaker = new Assignment(dataDir,
//				"polygonMakerLab", start, date(2015, 9, 4), false);
//		public final static Assignment Squiral = new Assignment(dataDir,
//				"squiralHW", start, date(2015, 9, 13), false);

		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", date(2015, 9, 18), false, true, null) {
			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did their GG1 work under none, but also did a short load/export under GG1
					// so the detection algorithm misses it
					case "0cc151f3-a9db-4a03-9671-d5c814b3bbbe": return None;
				}
				return super.getLocationAssignment(attemptID);
			}
		};

		public final static Assignment GuessingGame2 = new Assignment(instance,
				"guess2HW", date(2015, 9, 25), false, false, GuessingGame1) {
			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did GG2 on GG1, but then edited some more on "none", so no complete file
					case "10e87347-75ca-4d07-9b65-138c47332aca":
					// Did GG2 under GG1, but also has "none" work so this is to override that
					case "0cc151f3-a9db-4a03-9671-d5c814b3bbbe":
						return GuessingGame1;
					// Did their GG2 work under GG3, which includes no GG3 work...
					case "94833254-29ae-428a-b800-4a7efc699ef4":
						return GuessingGame3;
				}
				return super.getLocationAssignment(attemptID);
			}

			@Override
			public Integer getSubmittedRow(String attemptID) {
				switch (attemptID) {
					// Accidentally submitted GG1 again for GG2, but did export GG2
					case "4c9d2e1c-7cac-4b66-9784-231a62e36c0d": return 28092;
					// Did GG2 work under GG1 but then made insignificant edit under GG2
					case "5efff483-3746-4a25-bf9d-90d42a585d08": return 38188;
				}
				return super.getSubmittedRow(attemptID);
			};
		};

		public final static Assignment GuessingGame3 = new Assignment(instance,
				"guess3Lab", date(2015, 10, 2), false);

		public final static Assignment[] All = {
//			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};

		private Fall2015() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
	}

	public static class Spring2016 extends Dataset {

		public final static Spring2016 instance = new Spring2016();
		public final static Date start = date(2016, 1, 1);
		public final static String dataDir = BASE_DIR + "/spring2016";
		public final static String dataFile = dataDir + ".csv";

		public final static Assignment LightsCameraAction = new Assignment(instance,
				"lightsCameraActionHW", date(2016, 1, 29), true) {
			@Override
			public boolean ignore(String attemptID) {
				// Logs cut out part-way through
				return "8c515eec-6cad-444d-9882-41c596f415d0".equals(attemptID);
			};

		};
		public final static Assignment PolygonMaker = new Assignment(instance,
				"polygonMakerLab", date(2016, 2, 2), true);

		public final static Assignment Squiral = new Assignment(instance,
				"squiralHW", date(2016, 2, 9), true) {
			@Override
			public boolean ignore(String attemptID) {
				// Seems to be missing nearly all logs
				return "ebafbcc3-1834-43b9-9739-31940a5a4048".equals(attemptID) ||
						// Logs appear truncated; no export
						"4a9c4040-3646-498d-8a29-1a921237e6bb".equals(attemptID);
			}

			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did Squiral work during the lab
					case "74f1f0d0-9995-4846-9e41-fcd61606d6bf": return PolygonMaker;
				}
				return super.getLocationAssignment(attemptID);
			}
		};

		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", date(2016, 2, 9), true, true, null);

		public final static Assignment GuessingGame2 = new Assignment(instance,
				"guess2HW", date(2016, 2, 16), true, false, GuessingGame1) {
			@Override
			public boolean ignore(String attemptID) {
				// Just saves/loads under HW2
				return "33221f23-0ef8-4fb2-a950-79b6c20400e0".equals(attemptID);
			}
		};

		public final static Assignment GuessingGame3 = new Assignment(instance,
				"guess3Lab", date(2016, 2, 23), true) {
			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Seems to have done the assignment twice, but submitted under "None"
					case "8923de0f-491f-41d2-8c89-ad8a2cd24cf4":
						return None;
				}
				return super.getLocationAssignment(attemptID);
			}
		};

		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};

		private Spring2016() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
	}

	public static class Fall2016 extends Dataset {

		public final static Fall2016 instance = new Fall2016();
		public final static Date start = date(2015, 8, 17);
		public final static String dataDir = BASE_DIR + "/fall2016";
		public final static String dataFile = dataDir + ".csv";

		public final static Assignment LightsCameraAction = new Assignment(instance,
				"lightsCameraActionHW", date(2016, 9, 2), true) {
			@Override
			public boolean ignore(String attemptID) {
				// In all cases, logging cuts out part way through
				return "136a29fd-8591-4dbe-81eb-1b62e5366670".equals(attemptID) ||
						"c8889470-75ee-4b8b-a238-42cae2521fa7".equals(attemptID) ||
						"4b1cb6f6-48b1-428d-8eef-88216555b7c7".equals(attemptID) ||
						"8b69ceb5-424c-4541-8b4a-53221908b20b".equals(attemptID);
			}
		};

		public final static Assignment PolygonMaker = new Assignment(instance,
				"polygonMakerLab", date(2016, 9, 2), true) {
			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did work under wrong assignment
					case "a06d3d78-e0a5-4a19-bc44-2cace3bea77a": return Squiral;
				}
				return super.getLocationAssignment(attemptID);
			}
		};

		public final static Assignment Squiral = new Assignment(instance,
				"squiralHW", date(2016, 9, 11), true) {
			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did both inlab and homework under the same assignment
					case "b15bace5-74b5-4b0e-9592-e541c47b8678":
						return PolygonMaker;
				}
				return super.getLocationAssignment(attemptID);
			}

			@Override
			public boolean ignore(String attemptID) {
				// Edited polygonmaker submission, but no logs data from work on Squiral
				return "688aa42b-ca96-4bdc-b874-9d32c461fda8".equals(attemptID);
			}

			@Override
			public Integer getSubmittedRow(String attemptID) {
				switch (attemptID) {
					// Briefly changed to "none" and changed small things before submitting
					case "308e9a37-8630-481c-b983-d1803f5b83ee": return 172189;
				}
				return super.getSubmittedRow(attemptID);
			}
		};

		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", date(2016, 9, 16), true, true, null) {
			@Override
			public boolean ignore(String attemptID) {
				// Somehow managed to do GG2 before GG1... I don't even...
				return "66d1bd35-1013-45f7-a25c-101c31d36930".equals(attemptID);
			}

			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did both in-lab and homework under the HW assignment
					case "3ae58700-4575-4bbe-af81-579f02460c8e":
					// Did only the in-lab under the HW assignment
					case "495c88a5-536f-470e-bd6d-7b1c2e416002":
					case "92ce1f93-3392-4f5b-a111-c17bac56aee0":
						return GuessingGame2;
					// Did both in-lab and homework under the wrong assignment
					case "bba76353-ad96-489e-9e62-c264fedd03cd":
						return PolygonMaker;
				}
				return super.getLocationAssignment(attemptID);
			}

			@Override
			public Integer getSubmittedRow(String attemptID) {
				switch (attemptID) {
					// Went back to slightly edit GG1 later (after GG2), but version this was nearly
					// the same.
					case "b3a92bca-d9bc-4553-9498-9cb9feb2ef09": return 206491;
				}
				return super.getSubmittedRow(attemptID);
			}
		};

		public final static Assignment GuessingGame2 = new Assignment(instance,
				"guess2HW", date(2016, 9, 23), true, false, GuessingGame1) {
			@Override
			public boolean ignore(String attemptID) {
				// (See GG1 ignore)
				return "66d1bd35-1013-45f7-a25c-101c31d36930".equals(attemptID);
			}

			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did both in-lab and homework under the wrong assignment
					case "bba76353-ad96-489e-9e62-c264fedd03cd":
						return PolygonMaker;
					// Did both in-lab and homework under the in-lab
					case "94e0ca71-2969-4618-ac31-756a69ba1916":
						return GuessingGame1;
				}
				return super.getLocationAssignment(attemptID);
			}
		};


		public final static Assignment GuessingGame3 = new Assignment(instance,
				"guess3Lab", date(2016, 9, 30), true) {
			@Override
			public boolean ignore(String attemptID) {
				// Just imported/exported without creating any logs
				return "6d9879f6-c720-4896-adf7-eb2a68f28fe9".equals(attemptID);
			};

			@Override
			public Assignment getLocationAssignment(String attemptID) {
				switch (attemptID) {
					// Did GG2 and GG3 under GG2
					case "70481592-09e8-45e4-968b-6890078e2de7":
						return GuessingGame2;
				}
				return super.getLocationAssignment(attemptID);
			};
		};

		public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};

		private Fall2016() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
	}

	/** Demo dataset from http://go.ncsu.edu/isnap - publicly sharable data */
	public static class HelpSeeking extends Dataset {

		public final static HelpSeeking instance = new HelpSeeking();
		public final static Date start = date(2016, 8, 10);
		public final static String dataDir = "../data/help-seeking/experts2016";
		public final static String dataFile = dataDir + ".csv";

		private final static HintConfig config = new HintConfig();
		static {
			config.pruneGoals = 1;
			config.pruneNodes = 0;
		}

		public final static Assignment BrickWall = new Assignment(instance,
				"brickWall", null, true) {
			@Override
			public HintConfig config() {
				return config;
			};
		};
		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", null, true);

		public final static Assignment[] All = {
			BrickWall, GuessingGame1
		};

		private HelpSeeking() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
	}

	public static Date date(int year, int month, int day) {
		// NOTE: GregorianCalendar months are 0-based, thus the 'month - 1'
		return new GregorianCalendar(year, month - 1, day).getTime();
	}
}
