package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Dataset;

public abstract class CSC200Dataset extends Dataset {

	public final static String COURSE_ID = "UnivNonMajorsCourse";

	public CSC200Dataset(Date start, String dataDir) {
		super(start, dataDir);
	}

	@Override
	public String courseID() {
		return COURSE_ID;
	}
}
