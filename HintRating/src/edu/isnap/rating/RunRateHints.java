package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.IOException;

public class RunRateHints {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		RateHints.rateDir(RateHints.ITAP_DATA_DIR, RatingConfig.Python);
	}
}
