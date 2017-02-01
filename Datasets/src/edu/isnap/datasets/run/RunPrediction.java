package edu.isnap.datasets.run;

import edu.isnap.datasets.Fall2016;
import edu.isnap.predict.HighlightPrediction;

public class RunPrediction {
	public static void main(String[] args) {
		new HighlightPrediction().predict(Fall2016.Squiral);
	}
}
