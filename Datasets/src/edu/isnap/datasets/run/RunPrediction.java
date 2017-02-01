package edu.isnap.datasets.run;

import edu.isnap.datasets.Fall2015;
import edu.isnap.datasets.Fall2016;
import edu.isnap.predict.RootPathPrediction;

public class RunPrediction {
	public static void main(String[] args) {
		new RootPathPrediction().predict(Fall2016.GuessingGame2, Fall2015.GuessingGame2);
	}
}
