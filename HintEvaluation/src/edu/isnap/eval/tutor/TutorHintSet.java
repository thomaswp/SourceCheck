package edu.isnap.eval.tutor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.data.HintSet;
import edu.isnap.rating.data.TutorHint;

public class TutorHintSet extends HintSet {

	private List<TutorHint> tutorHints;

	public TutorHintSet(String name, RatingConfig config, List<? extends TutorHint> hints) {
		super(name, config);
		// TODO: Should rename snapshot -> Snap!shot
		this.tutorHints = hints.stream().map(e -> (TutorHint) e).collect(Collectors.toList());
		hints.forEach(hint -> add(hint.toOutcome()));
	}

	public static TutorHintSet combine(String name, TutorHintSet... hintSets) {
		if (hintSets.length == 0) {
			throw new IllegalArgumentException("Must have at least one TutorHintSet");
		}
		List<TutorHint> allHints = Arrays.stream(hintSets)
				.flatMap(set -> set.tutorHints.stream())
				.collect(Collectors.toList());
		return new TutorHintSet(name, hintSets[0].config, allHints);
	}
}
