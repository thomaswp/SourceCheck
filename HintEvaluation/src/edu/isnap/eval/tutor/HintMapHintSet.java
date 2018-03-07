package edu.isnap.eval.tutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.python.PythonHintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.TrainingDataset.Trace;

public abstract class HintMapHintSet extends HintSet {

	protected final HintConfig hintConfig;

	public abstract HintMapHintSet addHints(List<HintRequest> requests);

	public HintMapHintSet(String name, HintConfig hintConfig) {
		super(name, getRatingConfig(hintConfig));
		this.hintConfig = hintConfig;
	}

	public static RatingConfig getRatingConfig(HintConfig config) {
		if (config instanceof SnapHintConfig) return RatingConfig.Snap;
		if (config instanceof PythonHintConfig) return RatingConfig.Python;
		System.err.println("Unknown hint config: " + config.getClass().getName());
		return RatingConfig.Default;
	}

	public HintMapHintSet addHints(GoldStandard standard) {
		return addHints(standard.getHintRequests());
	}

	public static Node copyWithIDs(Node node) {
		return copyWithIDs(node, null, new AtomicInteger(0));
	}

	private static Node copyWithIDs(Node node, Node parent, AtomicInteger count) {
		String id = node.id;
		if (id == null) id = "GEN_" + count.getAndIncrement();
		Node copy = node.constructNode(parent, node.type(), node.value, id);
		for (Node child : node.children) {
			copy.children.add(copyWithIDs(child, copy, count));
		}
		return copy;
	}

	protected HintMapBuilder createHintBuilder(HintConfig hintConfig, List<Trace> traces) {
		HintMapBuilder builder = new HintMapBuilder(new HintMap(hintConfig), 1);
		for (Trace trace : traces) {
			List<Node> nodes = trace.stream()
					.map(node -> JsonAST.toNode(node, SnapNode::new))
					.collect(Collectors.toList());
			builder.addAttempt(nodes, config.areNodeIDsConsistent());
		}
		builder.finishedAdding();
		return builder;
	}
}
