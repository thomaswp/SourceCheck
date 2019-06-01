package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;

public class Fall2018 extends CSC200Dataset {

	public final static Date start = Assignment.date(2018, 8, 15);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/fall2018";
	public final static String dataFile = dataDir + ".csv";
	public final static Fall2018 instance = new Fall2018();

	// TODO: Add due dates
	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", null, true) {
	};

	/** Actually a lab */
	public final static Assignment Squiral = new Assignment(instance,
			"squiralHW", null, true, false, null) {
	};

	/** Actually a homework */
	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", null, true, false, Squiral) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("9e6b2c2e-5feb-4a10-80c9-278071f1cf84".equals(attemptID) ||
					"b23ea84f-11fe-4191-971f-c14907c06447".equals(attemptID)) {
				return Squiral;
			}
			return super.getLocationAssignment(attemptID);
		}

		@Override
		public Integer getSubmittedRow(String attemptID) {
			if ("2b08b082-8e75-48d9-8b53-cdd9f7d3dc7a".equals(attemptID)) return 122360;
			return super.getSubmittedRow(attemptID);
		};
	};

	public final static Assignment Pong1 = new Assignment(instance,
			"pong1Lab", null, true, false, null) {

		@Override
		public Integer getSubmittedRow(String attemptID) {
			if ("5c24260a-d8b0-474f-b9d4-30d9ffbdb6f2".equals(attemptID)) return 154803;
			return super.getSubmittedRow(attemptID);
		};
	};

	/** Snowstorm disrupted much of data collection: at least 6 submissions not done in iSnap */
	public final static Assignment Pong2 = new Assignment(instance,
			"pong2HW", null, true, false, Pong1) {

		// TODO: The project b300be20-adfb-4488-82eb-d5f4f783b6cc has two differnt submissions by
		// different students. They appear to have different traces, so it's not just cheating
		// (at least not _just_ cheating). I renamed one to have a '+' at the end, effectively
		// removing it. When we finally refactor to store attempts by their user id, not project id,
		// we need to reparse this assignment to include both
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab", null, true, false, null) {
	};

	/** Actually a homework */
	public final static Assignment GuessingGame2 = new Assignment(instance,
			"guess2Lab", null, true, false, GuessingGame1) {
		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("dec1802b-f3b5-48a3-aec6-61a46f666060".equals(attemptID)) return Pong1;
			return super.getLocationAssignment(attemptID);
		};
	};

	public final static Assignment Project = new Assignment(instance,
			"project", null, true, false, null) {
	};

	public final static Assignment[] All = {
		LightsCameraAction, PolygonMaker, Squiral,
		Pong1, Pong2, GuessingGame1, GuessingGame2
	};

	private Fall2018() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

	@Override
	public String getToolInstances() {
		return "iSnap v2.5.2; SourceCheck/Templates v1.4.0";
	}
}