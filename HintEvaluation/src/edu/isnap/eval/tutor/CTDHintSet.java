package edu.isnap.eval.tutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.hint.CTDHintGenerator;
import edu.isnap.ctd.hint.CTDModel;
import edu.isnap.ctd.hint.VectorHint;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.IDataConsumer;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.rating.data.HintOutcome;
import edu.isnap.rating.data.HintRequest;
import edu.isnap.rating.data.Trace;
import edu.isnap.rating.data.TrainingDataset;

public class CTDHintSet extends HintDataHintSet{

	private final Map<String, CTDHintGenerator> generators = new HashMap<>();

	@Override
	public IDataConsumer getDataConsumer() {
		return CTDHintGenerator.DataConsumer;
	}

	public CTDHintSet(String name, HintConfig hintConfig, TrainingDataset dataset) {
		super(name, hintConfig);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> traces = dataset.getTraces(assignmentID);
			HintData hintData = createHintData(assignmentID, hintConfig, traces);
			generators.put(assignmentID, hintData.getData(CTDModel.class).hintGenerator());
		}
	}

	@Override
	public CTDHintSet addHints(List<HintRequest> requests) {
		for (HintRequest request : requests) {
			CTDHintGenerator generator = generators.get(request.assignmentID);
			Node code = JsonAST.toNode(request.code, hintConfig.getNodeConstructor());

			code = hintConfig.areNodeIDsConsistent() ? code.copy() : copyWithIDs(code);

			List<VectorHint> hints = generator.getHints(code);
			for (VectorHint hint : hints) {
				Node to = hint.outcome().root();
				ASTNode outcomeNode = to.toASTNode();
				if (outcomeNode.hasType("snapshot")) outcomeNode.type = "Snap!shot";
				HintOutcome outcome = new HintOutcome(outcomeNode, request.assignmentID,
						request.id, 1);
				add(outcome);
			}
		}
		finish();
		return this;
	}


}
