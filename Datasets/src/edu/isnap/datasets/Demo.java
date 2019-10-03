package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;

/** Demo dataset from http://go.ncsu.edu/isnap - publicly sharable data */
public class Demo extends Dataset {

	public final static Date start = Assignment.date(2015, 8, 10);
	public final static String dataDir = "../data/demo";
	public final static String dataFile = dataDir + ".csv";
	public final static Demo instance = new Demo();

	public final static Assignment LightsCameraAction = new ConfigurableAssignment(instance,
			"lightsCameraActionHW", null, true);
	public final static Assignment PolygonMaker = new ConfigurableAssignment(instance,
			"polygonMakerLab", null, true);
	public final static Assignment Squiral = new ConfigurableAssignment(instance,
			"squiralHW", null, true);
	public final static Assignment GuessingGame1 = new ConfigurableAssignment(instance,
			"guess1Lab", null, true, true, null);
	public final static Assignment GuessingGame2 = new ConfigurableAssignment(instance,
			"guess2HW", null, true, false, GuessingGame1);
	public final static Assignment GuessingGame3 = new ConfigurableAssignment(instance,
			"guess3Lab", null, true);

	public final static Assignment[] All = {
			LightsCameraAction, PolygonMaker, Squiral,
			GuessingGame1, GuessingGame2, GuessingGame3
		};

		private Demo() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
}