package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class Fall2017 extends Dataset {

	public final static Date start = Assignment.date(2017, 8, 15);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/fall2017";
	public final static String dataFile = dataDir + ".csv";
	public final static Fall2017 instance = new Fall2017();

	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2017, 9, 1), true) {
	};

	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", Assignment.date(2017, 9, 1), true, false, null) {
	};

	public final static Assignment Squiral = new Assignment(instance,
			"squiralHW", Assignment.date(2017, 9, 9), true, false, null) {

		boolean wasLoggingUnstable(String id) {
			return "3d3c3ba4-fa83-4ced-b6b8-29c4c0ba56c1".equals(id);
		}
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab", Assignment.date(2017, 9, 8), true, false, null) {

		boolean wasLoggingUnstable(String id) {
			return "3ee80769-6131-467e-957f-b5cc5b8afc6f".equals(id) ||
					"7d70dc70-6811-448f-b222-59a01fcf0bd9".equals(id) ||
					"1758ee25-8566-48a9-9bbe-cc4f21617f1e".equals(id) ||
					"196e801a-7cea-4481-81ce-6325cf219765".equals(id);
		}

		@Override
		public Integer getSubmittedRow(String attemptID) {
			switch (attemptID) {
			case "85ba2644-7df1-4234-b06a-95789b84edb0": return 170468;
			}
			return super.getSubmittedRow(attemptID);
		};
	};

	// NOTE: One student plagiarized another, and I have removed the copied submission, since the
	// second student did no actual work, logged or otherwise
	public final static Assignment GuessingGame2 = new Assignment(instance,
			"guess2HW", Assignment.date(2017, 9, 15), true, false, GuessingGame1) {
	};


	public final static Assignment GuessingGame3 = new Assignment(instance,
			"guess3Lab", Assignment.date(2017, 9, 15), true, false, GuessingGame1) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("5e0613cb-162d-4e6e-b779-e8c221a8e3dd".equals(attemptID)) return GuessingGame2;
			return super.getLocationAssignment(attemptID);
		};

		@Override
		public Integer getSubmittedRow(String attemptID) {
			if ("5e0613cb-162d-4e6e-b779-e8c221a8e3dd".equals(attemptID)) return 197202;
			return super.getSubmittedRow(attemptID);
		};
	};

	public final static Assignment Project = new Assignment(instance,
			"project", Assignment.date(2017, 9, 22), true, false, null) {
	};

	public final static Assignment[] All = {
		LightsCameraAction, PolygonMaker, Squiral,
		GuessingGame1, GuessingGame2, GuessingGame3
	};

	private Fall2017() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}