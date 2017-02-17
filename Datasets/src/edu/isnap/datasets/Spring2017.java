package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class Spring2017 extends Dataset {

	public final static Spring2017 instance = new Spring2017();
	public final static Date start = Assignment.date(2017, 1, 1);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/spring2017";
	public final static String dataFile = dataDir + ".csv";

	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2016, 2, 2), true) {
	};

	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", Assignment.date(2016, 2, 2), true, true, null) {
	};

	public final static Assignment Squiral = new Assignment(instance,
			"squiralHW", Assignment.date(2016, 2, 9), true, true, null) {
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab", Assignment.date(2016, 2, 9), true, true, null) {
	};

	public final static Assignment GuessingGame2 = new Assignment(instance,
			"guess2HW", Assignment.date(2016, 2, 16), true, true, GuessingGame1) {
	};


	public final static Assignment GuessingGame3 = new Assignment(instance,
			"guess3Lab", Assignment.date(2016, 2, 23), true, true, null) {
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