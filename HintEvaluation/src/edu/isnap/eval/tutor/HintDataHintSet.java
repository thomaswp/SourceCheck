package edu.isnap.eval.tutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.IDataConsumer;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.node.Node;
import edu.isnap.python.PythonHintConfig;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.data.HintRequest;
import edu.isnap.rating.data.HintRequestDataset;
import edu.isnap.rating.data.HintSet;
import edu.isnap.rating.data.Trace;

public abstract class HintDataHintSet extends HintSet {

	protected final HintConfig hintConfig;

	public abstract HintDataHintSet addHints(List<HintRequest> requests);
	public abstract IDataConsumer getDataConsumer();

	public HintDataHintSet(String name, HintConfig hintConfig) {
		super(name, getRatingConfig(hintConfig));
		this.hintConfig = hintConfig;
	}

	public static RatingConfig getRatingConfig(HintConfig config) {
		if (config instanceof SnapHintConfig) return RatingConfig.Snap;
		if (config instanceof PythonHintConfig) return RatingConfig.Python;
		System.err.println("Unknown hint config: " + config.getClass().getName());
		return RatingConfig.Default;
	}

	public HintDataHintSet addHints(HintRequestDataset requestDataset) {
		return addHints(requestDataset.getAllRequests());
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

	protected HintData createHintData(String assignmentID, HintConfig hintConfig,
			List<Trace> traces) {
		HintData hintData = new HintData(assignmentID, hintConfig, 1, getDataConsumer());
		for (Trace trace : traces) {
			List<Node> nodes = trace.stream()
					.map(node -> JsonAST.toNode(node, SnapNode::new))
					.collect(Collectors.toList());
			hintData.addTrace(trace.id, nodes);
		}
		hintData.finished();
		return hintData;
	}
}
