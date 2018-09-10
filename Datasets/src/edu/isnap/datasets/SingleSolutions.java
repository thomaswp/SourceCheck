package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintConfig;

public class SingleSolutions extends Dataset {

	public final static Date start = null;
	public final static String dataDir = "../data/csc200/solutions/single";
	public final static SingleSolutions instance = new SingleSolutions();

	private static class CSC200Assignment extends ConfigurableAssignment {

		public CSC200Assignment(String name) {
			super(instance, name, null, false);
		}

		@Override
		public SnapHintConfig getConfig() {
			SnapHintConfig config = new SnapHintConfig();
			config.preprocessSolutions = false;
			return config;
		}
	}

	public final static Assignment Squiral = new CSC200Assignment("squiralHW");
	public final static Assignment GuessingGame1 = new CSC200Assignment("guess1Lab");

	public final static Assignment[] All = {
		Squiral,
		GuessingGame1,
	};

	private SingleSolutions() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

}