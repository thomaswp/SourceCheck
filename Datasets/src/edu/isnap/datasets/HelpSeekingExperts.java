package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;

	public class HelpSeekingExperts extends Dataset {

		public final static Date start = Assignment.date(2016, 8, 10);
		public final static String dataDir = "../data/help-seeking/experts2016";
		public final static String dataFile = dataDir + ".csv";
		public final static HelpSeekingExperts instance = new HelpSeekingExperts();

		private final static HintConfig config = new HintConfig();
		static {
			config.pruneGoals = 1;
			config.pruneNodes = 0;
			config.stayProportion = 0.55;
			// Custom block order seems pretty meaningless here, or at least hard to use effectively
			// with so little data, so we just ignore it. (And there's only 1 sprite.)
			config.badContext.add("snapshot");
		}

		public final static Assignment BrickWall = new ConfigurableAssignment(instance,
				"brickWall", null, true) {
			@Override
			public HintConfig getConfig() {
				return config;
			};
		};
		public final static Assignment GuessingGame1 = new Assignment(instance,
				"guess1Lab", null, true);

		public final static Assignment[] All = {
			BrickWall, GuessingGame1
		};

		private HelpSeekingExperts() {
			super(start, dataDir);
		}

		@Override
		public Assignment[] all() {
			return All;
		}
	}