package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;

public class Samples extends Dataset {

	public Samples() {
		super(start, dataDir);
	}

	public final static Date start = Assignment.date(2017, 1, 1);
	public final static String dataDir = "../data/samples";
	public final static String dataFile = dataDir + ".csv";
	public final static Samples instance = new Samples();

	public final static Assignment Square =
			new ConfigurableAssignment(instance, "square", null, true);

	public final static Assignment[] All = {
		Square
	};

	@Override
	public Assignment[] all() {
		return All;
	}
}
