package edu.isnap.eval.tutor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.TrainingDataset;
import edu.isnap.rating.TrainingDataset.Trace;

public class ImportHighlightHintSet extends HighlightHintSet {

	private final Map<String, HintHighlighter> highlighters = new HashMap<>();

	public ImportHighlightHintSet(String name, HintConfig hintConfig, String directory)
			throws IOException {
		this(name, hintConfig, TrainingDataset.fromDirectory(name, directory));
	}

	public ImportHighlightHintSet(String name, HintConfig hintConfig, TrainingDataset dataset) {
		super(name, hintConfig);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> traces = dataset.getTraces(assignmentID);
			HintMapBuilder builder = createHintBuilder(hintConfig, traces);
			highlighters.put(assignmentID, builder.hintHighlighter());
		}
	}

	@Override
	protected HintHighlighter getHighlighter(HintRequest request, HintMap baseMap) {
		return highlighters.get(request.assignmentID);
	}

}
