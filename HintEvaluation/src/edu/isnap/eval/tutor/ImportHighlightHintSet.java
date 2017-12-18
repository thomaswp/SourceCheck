package edu.isnap.eval.tutor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.rating.HintRequest;

public class ImportHighlightHintSet extends HighlightHintSet {

	private final Map<String, HintHighlighter> highlighters = new HashMap<>();

	public ImportHighlightHintSet(String name, HintConfig config, String directory) {
		super(name, config);
		File dirFile = new File(directory);
		if (!dirFile.exists()) {
			throw new RuntimeException("Directory does not exist: " +
					dirFile.getAbsolutePath());
		}
		for (File assignmentDir : dirFile.listFiles(f -> f.isDirectory())) {
			addAssignment(assignmentDir);
		};
	}

	private void addAssignment(File directory) {
		String assignment = directory.getName();
		HintMapBuilder builder = new HintMapBuilder(new HintMap(config), 1);
		for (File attemptDir : directory.listFiles(f -> f.isDirectory())) {
			List<Node> trace = new ArrayList<>();
			for (File snapshotFile : attemptDir.listFiles()) {
				try {
					if (!snapshotFile.getName().toLowerCase().endsWith(".json")) {
						System.err.println("Unknown file: " + snapshotFile.getAbsolutePath());
						continue;
					}
					String source = new String(Files.readAllBytes(snapshotFile.toPath()));
					ASTNode node = ASTNode.parse(source);
					trace.add(JsonAST.toNode(node, SnapNode::new));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// TODO: Decide how to handle data where IDs are inconsistent
			builder.addAttempt(trace, true);
		}
		builder.finishedAdding();
		highlighters.put(assignment, builder.hintHighlighter());
	}

	@Override
	protected HintHighlighter getHighlighter(HintRequest request, HintMap baseMap) {
		return highlighters.get(request.assignmentID);
	}

}
