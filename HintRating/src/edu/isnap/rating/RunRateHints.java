package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.IOException;

import edu.isnap.rating.TutorHint.Validity;

@SuppressWarnings("unused")
public class RunRateHints {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		Validity validity = Validity.MultipleTutors;

//		RateHints.rateOneDir(RateHints.ISNAP_F16_F17_DATA_DIR, "SourceCheck", RatingConfig.Snap,
//				validity, true, false);
//		RateHints.rateOneDir(RateHints.ITAP_S16_DATA_DIR, "SourceCheck", RatingConfig.Snap,
//				validity, true, false);

		// TODO: SourceCheck (possible others as well) gets different ratings with this method
		// and the above. Just on ITAP dataset it seems.
		RateHints.rateDir(RateHints.ISNAP_F16_F17_DATA_DIR, RatingConfig.Snap, validity, true);
		RateHints.rateDir(RateHints.ITAP_S16_DATA_DIR, RatingConfig.Python, validity, true);

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
