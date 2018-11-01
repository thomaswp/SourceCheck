package edu.isnap.hint;

import java.util.List;

import edu.isnap.node.Node;

public class HintData {

	@SuppressWarnings("unused")
	private HintData() { this(null, null, 0); }

	public String assignment;
	public double minGrade;
	public HintConfig config;
	private final IDataModel[] consumers;

	public HintData(String assignment, HintConfig config, double minGrade,
			IDataModel... consumers) {
		this.assignment = assignment;
		this.config = config;
		this.assignment = assignment;
		this.consumers = consumers;
	}

//	public HintHighlighter getHighlighter() {
//		return new Hint
//	}

	public <T extends IDataModel> T getData(Class<T> clazz) {
		for (IDataModel consumer : consumers) {
			if (clazz.isInstance(consumer)) return clazz.cast(consumer);
		}
		return null;
	}

	public void addTrace(String id, List<Node> trace) {
		for (IDataModel consumer : consumers) {
			consumer.addTrace(id, trace);
		}
	}

	public void finished() {
		for (IDataModel consumer : consumers) {
			consumer.finished();
		}
	}
}
