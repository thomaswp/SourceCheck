package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.IOException;

@SuppressWarnings("unused")
public class RunRateHints {

	public static void main(String[] args) throws FileNotFoundException, IOException {
//		RateHints.rateOneDir(RateHints.ISNAP_F16_F17_DATA_DIR, "SourceCheck", RatingConfig.Snap, false);
		RateHints.rateDir(RateHints.ISNAP_F16_F17_DATA_DIR, RatingConfig.Snap, true);
		RateHints.rateDir(RateHints.ITAP_S16_DATA_DIR, RatingConfig.Python, true);

//		printHints(RateHints.ISNAP_F16_F17_DATA_DIR, "chf_with_past", RatingConfig.Snap);
//		printHints(RateHints.ISNAP_F16_F17_DATA_DIR, "chf_without_past", RatingConfig.Snap);
//		printHints(RateHints.ITAP_S16_DATA_DIR, "chf_with_past", RatingConfig.Python);
//		printHints(RateHints.ITAP_S16_DATA_DIR, "chf_without_past", RatingConfig.Python);
	}

	private static void printHints(String dataset, String algorithm, RatingConfig config) {
		try {
			HintSet hints = HintSet.fromFolder(algorithm, config, String.format("%s%s/%s",
					dataset, RateHints.ALGORITHMS_DIR, algorithm));
			GoldStandard standard = GoldStandard.parseSpreadsheet(
					dataset + RateHints.GS_SPREADSHEET);
			hints.printHints(standard);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
