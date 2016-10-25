package com.snap.parser;

import java.util.Date;

/** Demo dataset from http://go.ncsu.edu/isnap - publicly sharable data */
	public class HelpSeeking extends Dataset {

		public final static HelpSeeking instance = new HelpSeeking();
		public final static Date start = Assignment.date(2016, 8, 10);
		public final static String dataDir = "../data/help-seeking/experts2016";
		public final static String dataFile = dataDir + ".csv";

//		private final static HintConfig config = new HintConfig();
//		static {
//			config.pruneGoals = 1;
//			config.pruneNodes = 0;
//		}

		public final static Assignment BrickWall = new Assignment(instance,
				"brickWall", null, true) {
//			@Override
//			public HintConfig config() {
//				return config;
//			};
		};
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