package edu.isnap.eval.tutor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.eval.tutor.TutorEdits.PrintableTutorHint;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.TutorHint;

public class TutorHintSet extends HintSet {

	public TutorHintSet(String name, RatingConfig config, List<TutorHint> hints) {
		super(name, config);
		hints.forEach(hint -> add(hint.toOutcome()));
	}

	public static TutorHintSet fromFile(String tutor, RatingConfig config, String filePath)
			throws FileNotFoundException, IOException {
		ListMap<String, PrintableTutorHint> edits = TutorEdits.readTutorEditsPython(filePath, null);
		List<TutorHint> hints = edits.values().stream()
				.flatMap(list -> list.stream())
				.filter(edit -> tutor.equals(edit.tutor))
				.collect(Collectors.toList());
		return new TutorHintSet(tutor, config, hints);
	}
}
