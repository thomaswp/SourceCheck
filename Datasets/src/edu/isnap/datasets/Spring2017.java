package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class Spring2017 extends Dataset {

	public final static Date start = Assignment.date(2017, 1, 1);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/spring2017";
	public final static String dataFile = dataDir + ".csv";
	public final static Spring2017 instance = new Spring2017();

	// TODO: resolve 5 missing assignments
	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2017, 2, 2), true) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			switch (attemptID) {
				// Did work under Project for some reason
				case "f6981651-60e8-4212-8c58-889e85e12c9f": return Project;
			}
			return super.getLocationAssignment(attemptID);
		}
	};

	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", Assignment.date(2017, 2, 2), true, false, null) {
	};

	public final static Assignment Squiral = new Assignment(instance,
			"squiralHW", Assignment.date(2017, 2, 9), true, false, null) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			switch (attemptID) {
				// Did work under the wrong assignment
				case "c87035d6-f406-4598-8a36-0cb92e482650": return PolygonMaker;
			}
			return super.getLocationAssignment(attemptID);
		};

		@Override
		public boolean ignore(String attemptID) {
			// Only one short viewing log under None, but no work
			return "d0393743-2646-40c6-92e9-1897927e1f82".equals(attemptID);
		};
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab", Assignment.date(2017, 2, 9), true, false, null) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			switch (attemptID) {
				// Did work under the wrong assignment
				case "73adfec1-3666-4499-96e6-5737e13d7806": return GuessingGame2;
			}
			return super.getLocationAssignment(attemptID);
		};
	};

	public final static Assignment GuessingGame2 = new Assignment(instance,
			"guess2HW", Assignment.date(2017, 2, 16), true, false, GuessingGame1) {

		@Override
		public boolean ignore(String attemptID) {
			// Missing most logs for some reason
			return "bba76353-ad96-489e-9e62-c264fedd03cd".equals(attemptID);
		};
	};


	public final static Assignment GuessingGame3 = new Assignment(instance,
			"guess3Lab", Assignment.date(2017, 2, 23), true, false, null) {

		@Override
		public boolean ignore(String attemptID) {
			return "81a8b07f-4f2c-4cd0-a84d-137c90c7c6b2".equals(attemptID);
		};
	};

	public final static Assignment Project = new Assignment(instance,
			"project", null, true, false, null) {
	};

	public final static Assignment[] All = {
		LightsCameraAction, PolygonMaker, Squiral,
		GuessingGame1, GuessingGame2, GuessingGame3
	};

	private Spring2017() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}