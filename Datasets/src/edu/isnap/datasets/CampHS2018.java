package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class CampHS2018 extends Dataset {

	public final static Date start = Assignment.date(2018, 7, 22);
	public final static String dataDir = "../data/camp/campHS2018";
	public final static String dataFile = dataDir + ".csv";
	public final static CampHS2018 instance = new CampHS2018();

	@Override
	public boolean onlyLogExportedCode() {
		return false;
	}

	public final static Assignment DaisyDesign = new Assignment(instance, "DaisyDesign");
	public final static Assignment SpiralPolygon = new Assignment(instance, "SpiralPolygon");
	public final static Assignment BrickWall = new Assignment(instance, "BrickWall");

	public final static Assignment[] All = {
		DaisyDesign, SpiralPolygon, BrickWall
	};

	private CampHS2018() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}
