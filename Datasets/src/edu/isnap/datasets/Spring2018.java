package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class Spring2018 extends Dataset {

	public final static Date start = Assignment.date(2018, 1, 6);
	public final static String dataDir = Assignment.CSC200_BASE_DIR + "/spring2018";
	public final static String dataFile = dataDir + ".csv";
	public final static Spring2018 instance = new Spring2018();

	public final static Assignment LightsCameraAction = new Assignment(instance,
			"lightsCameraActionHW", Assignment.date(2018, 1, 24), true) {
	};

	public final static Assignment PolygonMaker = new Assignment(instance,
			"polygonMakerLab", Assignment.date(2018, 1, 24), true, false, null) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("ae29ae0b-1dca-4f55-8ed7-e152d38f127d".equals(attemptID)) {
				return LightsCameraAction;
			}
			return super.getLocationAssignment(attemptID);
		}
	};

	// TODO: Currently this fails for most students. Need to fix! Ask Rui about it :P
	public final static Assignment SquiralObjectives = new Assignment(instance,
			"squiralHW", Assignment.date(2018, 1, 30), true, false, null) {
	};

	public final static Assignment Pong1 = new Assignment(instance,
			"pong1Lab", Assignment.date(2018, 1, 30), true, false, null) {
	};

	public final static Assignment Pong2 = new Assignment(instance,
			"pong2HW", Assignment.date(2018, 2, 2), true, false, Pong1) {
	};

	public final static Assignment GuessingGame1 = new Assignment(instance,
			"guess1Lab", Assignment.date(2018, 2, 2), true, false, null) {
		@Override
		public Assignment getLocationAssignment(String attemptID) {
			if ("b86cf4d7-8352-45f6-baba-cad302acc8bc".equals(attemptID)) {
				return Pong1;
			}
			return super.getLocationAssignment(attemptID);
		}

		@Override
		public boolean ignore(String attemptID) {
			return "c3b814f8-54d8-4bcf-9bf3-a7ec7e4518ab".equals(attemptID);
		};
	};

	/**
	 * The assignment changed in Spring 2018, so we've changed the name to GG2 Lab
	 */
	public final static Assignment GuessingGame2Lab = new Assignment(instance,
			"guess2Lab", Assignment.date(2018, 2, 14), true, false, GuessingGame1) {
	};

	public final static Assignment Project = new Assignment(instance,
			"project", Assignment.date(2018, 2, 26), true, false, null) {
	};

	public final static Assignment[] All = {
		LightsCameraAction, PolygonMaker, SquiralObjectives,
		Pong1, Pong2, GuessingGame1, GuessingGame2Lab
	};

	private Spring2018() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}