package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintConfig;

public class CSC200Solutions extends Dataset {

	public final static Date start = null;
	public final static String dataDir = "../data/csc200/solutions";
	public final static CSC200Solutions instance = new CSC200Solutions();

	private static class CSC200Assignment extends ConfigurableAssignment {

		public CSC200Assignment(String name) {
			super(instance, name, null, false);
		}

		@Override
		public HintConfig getConfig() {
			HintConfig config = new SnapHintConfig();
			config.preprocessSolutions = false;
			return config;
		}
	}

	public final static Assignment PolygonMaker = new CSC200Assignment("polygonMakerLab");
	public final static Assignment Squiral = new CSC200Assignment("squiralHW");
	public final static Assignment Pong1Lab = new CSC200Assignment("pong1Lab");
	public final static Assignment Pong2HW = new CSC200Assignment("pong2HW");
	public final static Assignment GuessingGame1 = new CSC200Assignment("guess1Lab");
	public final static Assignment GuessingGame2 = new CSC200Assignment("guess2HW");
	public final static Assignment GuessingGame3 = new CSC200Assignment("guess3Lab");
	public final static Assignment GuessingGame2New = new CSC200Assignment("guess2Lab");

	public final static Assignment[] All = {
		PolygonMaker,
		Squiral,
		GuessingGame1,
		GuessingGame2,
		GuessingGame3,
	};

	private CSC200Solutions() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

}