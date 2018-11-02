package edu.isnap.hint;

import java.util.ArrayList;
import java.util.List;

import edu.isnap.node.Node;

public class HintData {

	@SuppressWarnings("unused")
	private HintData() { this(null, null, 0); }

	public String assignment;
	public double minGrade;
	public HintConfig config;
	private final List<IDataModel> dataModels;

	public HintData(String assignment, HintConfig config, double minGrade,
			IDataConsumer... consumers) {
		this.assignment = assignment;
		this.config = config;
		this.assignment = assignment;

		this.dataModels = new ArrayList<>();
		for (IDataConsumer consumer : consumers) {
			addDataModels(consumer.getRequiredData(this));
		}
	}

	private void addDataModels(IDataModel[] models) {
		for (IDataModel model : models) {
			addDataModels(model.getDependencies(this));
			this.dataModels.add(model);
		}
	}

	public <T extends IDataModel> T getData(Class<T> clazz) {
		for (IDataModel model : dataModels) {
			if (clazz.isInstance(model)) return clazz.cast(model);
		}
		return null;
	}

	public void addTrace(String id, List<Node> trace) {
		for (IDataModel model : dataModels) {
			model.addTrace(id, trace);
		}
	}

	public void finished() {
		for (IDataModel model : dataModels) {
			model.finished();
		}
		for (IDataModel model : dataModels) {
			model.postProcess(this);
		}
	}
}
