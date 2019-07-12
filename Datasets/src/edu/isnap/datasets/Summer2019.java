package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;

// TODO: Check for students starting on different assignments (for all but GG1, SQ)
public class Summer2019 extends CSC200Dataset {

	public final static Date start = Assignment.date(2017, 1, 1);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/summer2019";
	public final static String dataFile = dataDir + ".csv";
	public final static Summer2019 instance = new Summer2019();

	// TODO: resolve 5 missing assignments
	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2027, 2, 2), true) {
	};

	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", Assignment.date(2027, 2, 2), true, false, null) {
	};


	public final static Assignment Squiral = new Assignment(instance,
			"squiralHW",Assignment.date(2027, 2, 2), true, false, null) {
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab",Assignment.date(2027, 2, 2), true, false, null) {
	};

	public final static Assignment GuessingGame2 = new Assignment(instance,
			"guess2HW",Assignment.date(2027, 2, 2), true, false, null) {
	};

	public final static Assignment GuessingGame3 = new Assignment(instance,
			"guess3Lab",Assignment.date(2027, 2, 2), true, false, null) {
	};

	public final static Assignment Project = new Assignment(instance,
			"project",Assignment.date(2027, 2, 2), true, false, null) {
	};
// Added starts for students starting on different assignments
//	public final static Assignment Squiral = new Assignment(instance,
//			"squiralHW", Assignment.date(2027, 2, 9), true, false, null) {
//
//		@Override
//		public Assignment getLocationAssignment(String attemptID) {
//			switch (attemptID) {
//				// Did work under the wrong assignment
//				case "c87035d6-f406-4598-8a36-0cb92e482650": return PolygonMaker;
//			}
//			return super.getLocationAssignment(attemptID);
//		};
//
//		@Override
//		public boolean ignore(String attemptID) {
//			// Only one short viewing log under None, but no work
//			return "d0393743-2646-40c6-92e9-1897927e1f82".equals(attemptID);
//		};
//	};

//	// Added starts for students starting on different assignments
//	public final static Assignment GuessingGame1 = new Assignment(instance,
//			"guess1Lab", Assignment.date(2027, 2, 9), true, false, null) {
//
//		@Override
//		public Assignment getLocationAssignment(String attemptID) {
//			switch (attemptID) {
//				// Did work under the wrong assignment
//				case "73adfec1-3666-4499-96e6-5737e13d7806": return GuessingGame2;
//			}
//			return super.getLocationAssignment(attemptID);
//		};
//
//		@Override
//		public Integer getSubmittedRow(String attemptID) {
//			switch (attemptID) {
//				// Made a small edit much later, after GG2, but export was essentially the same.
//				case "bb6fd568-3a1a-4990-bf38-a1aa3c9edde2": return 141498;
//			}
//			return super.getSubmittedRow(attemptID);
//		}
//
//		@Override
//		public boolean ignore(String attemptID) {
//			// Skipped straight to GG2 without doing GG1 on its own.
//			return "c654eec5-0ae4-4e09-8c48-634cbade53d5".equals(attemptID);
//		};
//	};
//
//	public final static Assignment GuessingGame2 = new Assignment(instance,
//			"guess2HW", Assignment.date(2027, 2, 16), true, false, GuessingGame1) {
//
//		@Override
//		public boolean ignore(String attemptID) {
//			// Missing most logs for some reason
//			return "bba76353-ad96-489e-9e62-c264fedd03cd".equals(attemptID);
//		};
//	};
//
//
//	public final static Assignment GuessingGame3 = new Assignment(instance,
//			"guess3Lab", Assignment.date(2027, 2, 23), true, false, null) {
//
//		@Override
//		public boolean ignore(String attemptID) {
//			return "81a8b07f-4f2c-4cd0-a84d-137c90c7c6b2".equals(attemptID);
//		};
//	};
//
//	public final static Assignment Project = new Assignment(instance,
//			"project", null, true, false, null) {
//	};

	public final static Assignment[] All = {
		LightsCameraAction, PolygonMaker,
		Squiral,
		GuessingGame1,
		GuessingGame2,
		GuessingGame3, Project
	};

	private Summer2019() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

	@Override
	public String getToolInstances() {
		return "iSnap v2.1.0; SourceCheck v1.2.1";
	}
}