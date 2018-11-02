package edu.isnap.hint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.isnap.node.Node;

public class HintData {

	@SuppressWarnings("unused")
	private HintData() { this(null, null, 0); }

	public String assignment;
	public double minGrade;
	public HintConfig config;
	private final IDataModel[] dataModels;

	public HintData(String assignment, HintConfig config, double minGrade,
			IDataConsumer... consumers) {
		this.assignment = assignment;
		this.config = config;
		this.assignment = assignment;

		Set<IDataModel> models = new HashSet<>();
		for (IDataConsumer consumer : consumers) {
			Arrays.stream(consumer.getRequiredData(this)).forEach(models::add);
		}
		dataModels = models.toArray(new IDataModel[models.size()]);

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
