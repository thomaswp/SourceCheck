package edu.isnap.datasets.csc200;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;


public class Spring2016 extends CSC200Dataset {

	public final static Date start = Assignment.date(2016, 1, 1);
	public final static String dataDir = CSC200_BASE_DIR + "/spring2016";
	public final static String dataFile = dataDir + ".csv";
	public final static Spring2016 instance = new Spring2016();

	public final static Assignment LightsCameraAction = new ConfigurableAssignment(instance,
			"lightsCameraActionHW", Assignment.date(2016, 1, 29), true) {
		@Override
		public boolean ignore(String attemptID) {
			// Logs cut out part-way through
			return "8c515eec-6cad-444d-9882-41c596f415d0".equals(attemptID);
		};

	};
	public final static Assignment PolygonMaker = new ConfigurableAssignment(instance,
			"polygonMakerLab", Assignment.date(2016, 2, 2), true);

	public final static Assignment Squiral = new ConfigurableAssignment(instance,
			"squiralHW", Assignment.date(2016, 2, 9), true) {
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

	public final static Assignment GuessingGame1 = new ConfigurableAssignment(instance,
			"guess1Lab", Assignment.date(2016, 2, 9), true);

	public final static Assignment GuessingGame2 = new ConfigurableAssignment(instance,
			"guess2HW", Assignment.date(2016, 2, 16), true, false, GuessingGame1) {
		@Override
		public boolean ignore(String attemptID) {
			// Just saves/loads under HW2
			return "33221f23-0ef8-4fb2-a950-79b6c20400e0".equals(attemptID);
		}
	};

	public final static Assignment GuessingGame3 = new ConfigurableAssignment(instance,
			"guess3Lab", Assignment.date(2016, 2, 23), true) {
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

	@Override
	public String getToolInstances() {
		return "iSnap v1.0; CTD v1.0";
	}
}