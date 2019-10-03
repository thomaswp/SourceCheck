package edu.isnap.datasets.csc110;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;


public class CSC110Fall2019 extends CSC110Dataset {

	public final static Date start = Assignment.date(2019, 8, 15);
	public final static String dataDir = CSC110_BASE_DIR + "/fall2019";
	public final static String dataFile = dataDir + ".csv";
	public final static CSC110Fall2019 instance = new CSC110Fall2019();

	public final static Assignment DaisyDesign = new ConfigurableAssignment(instance,
			"daisyDesignHW", null, true) {
	};
	public final static Assignment[] All = {
		DaisyDesign
	};

	private CSC110Fall2019() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

	@Override
	public String getToolInstances() {
		return "iSnap v2.5.2; SourceCheck/Templates v1.4.0";
	}
}