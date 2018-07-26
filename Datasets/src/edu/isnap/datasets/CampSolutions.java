package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintConfig;

public class CampSolutions extends Dataset {

	public final static Date start = Assignment.date(2018, 7, 11);
	public final static String dataDir = "../data/camp/solutions";
	public final static String dataFile = dataDir + ".csv";
	public final static CampSolutions instance = new CampSolutions();

	private static class Solution extends ConfigurableAssignment {

		public Solution(String name) {
			super(instance, name, null, false);
		}

		@Override
		public SnapHintConfig getConfig() {
			SnapHintConfig config = new SnapHintConfig();
			config.preprocessSolutions = false;
			return config;
		}
	}

	public final static Solution Practice = new Solution("practice");
	public final static Solution Frogger = new Solution("frogger");
	public final static Solution Asteroids = new Solution("asteroids");

	public final static Assignment[] All = {
		Frogger, Asteroids
	};

	private CampSolutions() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}