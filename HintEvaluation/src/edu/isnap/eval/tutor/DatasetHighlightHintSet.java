package edu.isnap.eval.tutor;

import java.util.HashMap;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintMap;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.NullStream;
import edu.isnap.parser.Store.Mode;
import edu.isnap.rating.data.HintRequest;
import edu.isnap.sourcecheck.HintHighlighter;

public class DatasetHighlightHintSet extends HighlightHintSet {


	private final Map<String, HintHighlighter> highlighters = new HashMap<>();
	private final Map<String, Assignment> assignmentMap;

	public DatasetHighlightHintSet(String name, HintConfig config, Dataset dataset) {
		super(name, config);
		assignmentMap = dataset.getAssignmentMap();
	}

	@Override
	protected HintHighlighter getHighlighter(HintRequest request, HintMap baseMap) {
		Assignment assignment = assignmentMap.get(request.assignmentID);
		HintHighlighter highlighter = highlighters.get(request.assignmentID);
		if (highlighter == null) {
			SnapHintBuilder builder = new SnapHintBuilder(assignment, baseMap);
			highlighter = builder.buildGenerator(Mode.Ignore, 1).hintHighlighter();
			highlighter.trace = NullStream.instance;
			highlighters.put(request.assignmentID, highlighter);
		}
		return highlighter;
	}

}
