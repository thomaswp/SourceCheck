package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class Fall2016 extends Dataset {

	public final static Fall2016 instance = new Fall2016();
	public final static Date start = Assignment.date(2015, 8, 17);
	public final static String dataDir = Assignment.BASE_DIR + "/fall2016";
	public final static String dataFile = dataDir + ".csv";

	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2016, 9, 2), true) {
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
			"polygonMakerLab", Assignment.date(2016, 9, 2), true) {
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
			"squiralHW", Assignment.date(2016, 9, 11), true) {
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
			"guess1Lab", Assignment.date(2016, 9, 16), true, true, null) {
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
			"guess2HW", Assignment.date(2016, 9, 23), true, false, GuessingGame1) {
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
			"guess3Lab", Assignment.date(2016, 9, 30), true) {
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