package edu.isnap.datasets.csc110;

import java.util.Date;

import edu.isnap.dataset.Dataset;

public abstract class CSC110Dataset extends Dataset {

	public final static String COURSE_ID = "Univ100NonMajorsCourse";
	public final static String CSC110_BASE_DIR = "../data/csc110";

	public CSC110Dataset(Date start, String dataDir) {
		super(start, dataDir);
	}

	@Override
	public String courseID() {
		return COURSE_ID;
	}
}
