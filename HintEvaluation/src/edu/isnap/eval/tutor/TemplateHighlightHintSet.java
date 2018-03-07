package edu.isnap.eval.tutor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.rating.HintRequest;
import edu.isnap.template.parse.TemplateParser;

public class TemplateHighlightHintSet extends HighlightHintSet {

	private final Map<String, HintHighlighter> highlighters = new HashMap<>();
	private final String baseDir;

	public TemplateHighlightHintSet(String name, Dataset dataset) {
		this(name, new SnapHintConfig(), dataset.dataDir);
	}

	public TemplateHighlightHintSet(String name, HintConfig config, String baseDir) {
		super(name, config);
		this.hintConfig.preprocessSolutions = false;
		this.baseDir = baseDir;
	}

	@Override
	protected HintHighlighter getHighlighter(HintRequest request, HintMap baseMap) {
		HintHighlighter highlighter = highlighters.get(request.assignmentID);
		if (highlighter == null) {
			Kryo kryo = SnapHintBuilder.getKryo();
			String path = SnapHintBuilder.getStorePath(
					baseDir, request.assignmentID, 1, TemplateParser.DATASET);
			try {
				InputStream stream = new FileInputStream(path);
				Input input = new Input(stream);
				HintMapBuilder builder = kryo.readObject(input, HintMapBuilder.class);
				highlighter = builder.hintHighlighter();
				highlighters.put(request.assignmentID, highlighter);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("Missing template file for assignment: " + path);
			}
		}
		return highlighter;
	}

}
