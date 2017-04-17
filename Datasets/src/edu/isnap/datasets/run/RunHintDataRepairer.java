package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.hint.HighlightDataRepairer;

public class RunHintDataRepairer {
	public static void main(String[] args) throws IOException {
		HighlightDataRepairer.repair(Spring2017.PolygonMaker, Fall2016.instance, 1);
	}
}
