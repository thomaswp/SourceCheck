package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class BJCSolutions2017 extends Dataset {

	public final static Date start = null;
	public final static String dataDir = "../data/bjc/solutions2017";
	public final static String dataFile = dataDir + ".csv";
	public final static BJCSolutions2017 instance = new BJCSolutions2017();

	public final static Assignment U1_L1_Alonzo = new Assignment(instance,
			"U1_L1_Alonzo", null, true, false, null);

	public final static Assignment U1_P1_LineArt = new Assignment(instance,
			"U1_P1_LineArt", null, true, false, null);

	public static final Assignment U1_L2_Gossip = new Assignment(instance,
			"U1_L2_Gossip", null, true, false, null);

	public final static Assignment[] All = {
			U1_L1_Alonzo,
			U1_L2_Gossip,
			U1_P1_LineArt
	};

	private BJCSolutions2017() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}