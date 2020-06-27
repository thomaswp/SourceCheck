package edu.isnap.hint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import edu.isnap.ctd.hint.CTDHintGenerator;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;

public class HintData {

	@SuppressWarnings("unused")
	private HintData() { this(null, null, 0, null); }

	public final String assignment;
	// TODO: Remove minGrade
	public final double minGrade;
	public final HintConfig config;
	private final List<IDataModel> dataModels;
	private LinkedHashMap<Integer, Node> referenceSolutions;

	/**
	 * @param assignment the name of java file used in this assignment. Omit ".java".
	 * @param config
	 * @param minGrade
	 * @param consumer
	 * @param additionalConsumers
	 */
	public HintData(String assignment, HintConfig config, double minGrade,
			IDataConsumer consumer, IDataConsumer... additionalConsumers) {
		this.assignment = assignment;
		this.minGrade = minGrade;
		this.config = config;

		this.dataModels = new ArrayList<>();
		if (consumer != null) addDataModels(consumer.getRequiredData(this));
		for (IDataConsumer con : additionalConsumers) {
			addDataModels(con.getRequiredData(this));
		}
		referenceSolutions = new LinkedHashMap<>();
	}

	private void addDataModels(IDataModel[] models) {
		for (IDataModel model : models) {
			boolean exists = false;
			for (IDataModel m : dataModels) {
				if (m.getClass() == model.getClass()) exists = true;
			}
			if (exists) continue;
			addDataModels(model.getDependencies(this));
			this.dataModels.add(model);
		}
	}

	public <T extends IDataModel> T getModel(Class<T> clazz) {
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

	// TODO: at some point these should maybe be removed
	public CTDHintGenerator hintGenerator() {
		return new CTDHintGenerator(this);
	}

	public HintHighlighter hintHighlighter() {
		return new HintHighlighter(this);
	}

	public LinkedHashMap<Integer, Node> getReferenceSolutions() {
		return referenceSolutions;
	}
	
	public Node addReferenceSoltion(int clusterID, Node solution) {
		return referenceSolutions.put(clusterID, solution);
	}
}
