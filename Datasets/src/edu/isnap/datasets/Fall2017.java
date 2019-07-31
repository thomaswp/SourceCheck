package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;


// TODO: Check for students starting on different assignments
public class Fall2017 extends CSC200Dataset {

	public final static Date start = Assignment.date(2017, 8, 15);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/fall2017";
	public final static String dataFile = dataDir + ".csv";
	public final static Fall2017 instance = new Fall2017();

	public final static Assignment LightsCameraAction = new ConfigurableAssignment(instance,
			"lightsCameraActionHW", Assignment.date(2017, 9, 1), true) {
	};

	public final static Assignment PolygonMaker = new ConfigurableAssignment(instance,
			"polygonMakerLab", Assignment.date(2017, 9, 1), true, false, null) {
	};

	// Added starts for students starting on different assignments
	public final static Assignment Squiral = new ConfigurableAssignment(instance,
			"squiralHW", Assignment.date(2017, 9, 9), true, false, null) {

		@Override
		public boolean wasLoggingUnstable(String id) {
					// Skips at 169137
			return "3d3c3ba4-fa83-4ced-b6b8-29c4c0ba56c1".equals(id);
		}
	};

	// Added starts for students starting on different assignments
	public final static Assignment GuessingGame1 = new ConfigurableAssignment(instance,
			"guess1Lab", Assignment.date(2017, 9, 8), true, false, null) {

		@Override
		public boolean wasLoggingUnstable(String id) {
					// Sips at 155358
			return "3ee80769-6131-467e-957f-b5cc5b8afc6f".equals(id) ||
					// Skips at 155351
					"7d70dc70-6811-448f-b222-59a01fcf0bd9".equals(id);
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
	public final static Assignment GuessingGame2 = new ConfigurableAssignment(instance,
			"guess2HW", Assignment.date(2017, 9, 15), true, false, GuessingGame1) {
	};

	public final static Assignment GuessingGame3 = new ConfigurableAssignment(instance,
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

	public final static Assignment Project = new ConfigurableAssignment(instance,
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

	@Override
	public String getToolInstances() {
		return "iSnap v2.4.3; SourceCheck v1.3.0";
	}
}