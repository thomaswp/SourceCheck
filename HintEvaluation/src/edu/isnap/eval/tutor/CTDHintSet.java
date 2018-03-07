package edu.isnap.eval.tutor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintGenerator;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.ctd.hint.VectorHint;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.rating.HintOutcome;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.TrainingDataset;
import edu.isnap.rating.TrainingDataset.Trace;

public class CTDHintSet extends HintMapHintSet{

	private final Map<String, HintGenerator> generators = new HashMap<>();

	public CTDHintSet(String name, HintConfig hintConfig, String directory)
			throws IOException {
		this(name, hintConfig, TrainingDataset.fromDirectory(name, directory));
	}

	public CTDHintSet(String name, HintConfig hintConfig, TrainingDataset dataset) {
		super(name, hintConfig);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> traces = dataset.getTraces(assignmentID);
			HintMapBuilder builder = createHintBuilder(hintConfig, traces);
			generators.put(assignmentID, builder.hintGenerator());
		}
	}

	@Override
	public CTDHintSet addHints(List<HintRequest> requests) {
		for (HintRequest request : requests) {
			HintGenerator generator = generators.get(request.assignmentID);
			Node code = JsonAST.toNode(request.code, hintConfig.getNodeConstructor());
			code = config.areNodeIDsConsistent() ? code.copy() : copyWithIDs(code);

			List<VectorHint> hints = generator.getHints(code);
			for (VectorHint hint : hints) {
				Node to = hint.outcome();
				ASTNode outcomeNode = to.toASTNode();
				if (outcomeNode.hasType("snapshot")) outcomeNode.type = "Snap!shot";
				HintOutcome outcome = new HintOutcome(outcomeNode, request.assignmentID,
						request.assignmentID, 1);
				add(outcome);
			}
		}
		finish();
		return this;
	}


}
