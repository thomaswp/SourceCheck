package edu.isnap.datasets.run;

import java.io.IOException;

import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.hint.HighlightDataRepairer;

public class RunHintDataRepairer {
	public static void main(String[] args) throws IOException {
		for (Assignment assignment : Spring2017.All) {
			HighlightDataRepairer.repair(assignment, Fall2016.instance, 1);
		}
	}
}
