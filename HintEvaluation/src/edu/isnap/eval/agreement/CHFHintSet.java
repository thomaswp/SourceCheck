package edu.isnap.eval.agreement;

import java.io.IOException;
import java.util.DoubleSummaryStatistics;
import java.util.List;

import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.eval.agreement.CHFImport.CHFEdit;
import edu.isnap.eval.agreement.RateHints.HintSet;
import edu.isnap.eval.agreement.RateHints.RatingConfig;

public class CHFHintSet extends HintSet {

	public CHFHintSet(String name, RatingConfig config, String folder, Assignment... assignments) {
		super(name, config);

		for (Assignment assignment : assignments) {
			ListMap<Integer, CHFEdit> hints;
			try {
				 hints = CHFImport.loadAllHints(assignment, folder);
			} catch (IOException e) {
				System.err.println("Unable to load assignment: " + assignment.name);
				e.printStackTrace();
				continue;
			}

			for (Integer id : hints.keySet()) {

				List<CHFEdit> edits = hints.get(id);

				DoubleSummaryStatistics errorStats = edits.stream()
					.mapToDouble(e -> e.error)
					.summaryStatistics();

				double minError = errorStats.getMin();
				double beta = 1 / (errorStats.getAverage() - minError);

				edits.forEach(e -> add(id, e.toOutcome(minError, beta)));
			}
		}

	}

}
