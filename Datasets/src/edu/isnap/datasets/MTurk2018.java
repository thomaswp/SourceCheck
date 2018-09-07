package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

// TODO: Check for students starting on different assignments
public class MTurk2018 extends Dataset {

	public final static Date start = null;
	public final static String dataDir = "../data/mturk/mturk2018";
	public final static String dataFile = dataDir + ".csv";
	public final static MTurk2018 instance = new MTurk2018();

	@Override
	public boolean onlyLogExportedCode() {
		return false;
	}
	
	public final static Assignment PolygonMakerSimple = new Assignment(instance,
			"polygonMakerSimple", null, true) {
	};
	
	public final static Assignment DrawTriangles = new Assignment(instance,
			"drawTriangles", null, true) {
	};

	public final static Assignment[] All = {
		PolygonMakerSimple, DrawTriangles
	};

	private MTurk2018() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}