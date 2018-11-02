package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;

public class Fall2018 extends Dataset {

	public final static Date start = Assignment.date(2018, 8, 15);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/fall2018";
	public final static String dataFile = dataDir + ".csv";
	public final static Fall2018 instance = new Fall2018();

	// TODO: Add due dates
	public final static Assignment LightsCameraAction = new ConfigurableAssignment(instance,
			"lightsCameraActionHW", null, true) {
	};

	public final static Assignment Squiral = new ConfigurableAssignment(instance,
			"squiralHW", null, true, false, null) {
	};

	public final static Assignment PolygonMaker = new ConfigurableAssignment(instance,
			"polygonMakerLab", null, true, false, null) {
	};

	public final static Assignment Pong1 = new ConfigurableAssignment(instance,
			"pong1Lab", null, true, false, null) {
	};

	public final static Assignment Pong2 = new ConfigurableAssignment(instance,
			"pong2HW", null, true, false, Pong1) {
	};

	public final static Assignment GuessingGame1 = new ConfigurableAssignment(instance,
			"guess1Lab", null, true, false, null) {
	};

	public final static Assignment GuessingGame2 = new ConfigurableAssignment(instance,
			"guess2Lab", null, true, false, GuessingGame1) {
	};

	public final static Assignment Project = new ConfigurableAssignment(instance,
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
}