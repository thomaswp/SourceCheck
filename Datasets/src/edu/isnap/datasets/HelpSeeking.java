package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

	public class HelpSeeking extends Dataset {

		public final static Date start = Assignment.date(2016, 8, 10);
		public final static String dataDir = "../data/help-seeking/fall2016-spring2017";
		public final static String dataFile = dataDir + ".csv";
		public final static HelpSeeking instance = new HelpSeeking();

		public final static Assignment BrickWall = new Assignment(instance,
				"brickWall", null, true);
		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", null, true);

		public final static Assignment[] All = {
			BrickWall, GuessingGame1
		};

		private HelpSeeking() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
	}