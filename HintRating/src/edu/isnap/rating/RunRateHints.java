package edu.isnap.rating;

import java.io.FileNotFoundException;
import java.io.IOException;

import edu.isnap.rating.data.GoldStandard;
import edu.isnap.rating.data.HintSet;
import edu.isnap.rating.data.TutorHint.Validity;

@SuppressWarnings("unused")
public class RunRateHints {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		Validity validity = Validity.MultipleTutors;

//		RateHints.rateOneDir(RateHints.ISNAP_F16_F17_DATA_DIR, "SourceCheck", RatingConfig.Snap,
//				validity, true, false);
//		RateHints.rateOneDir(RateHints.ITAP_S16_DATA_DIR, "SourceCheck", RatingConfig.Python,
//				validity, true, false);

		RateHints.rateDir(RateHints.ISNAP_F16_F17_DATA_DIR, RatingConfig.Snap, validity, true);
		RateHints.rateDir(RateHints.ITAP_S16_DATA_DIR, RatingConfig.Python, validity, true);

//		RateHints.rateDir(RateHints.ISNAP_F16_F17_DATA_DIR, RatingConfig.Snap,
//				Validity.OneTutor, true);
//		RateHints.rateDir(RateHints.ISNAP_F16_F17_DATA_DIR, RatingConfig.Snap,
//				Validity.MultipleTutors, true);
//		RateHints.rateDir(RateHints.ISNAP_F16_F17_DATA_DIR, RatingConfig.Snap,
//				Validity.Consensus, true);

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