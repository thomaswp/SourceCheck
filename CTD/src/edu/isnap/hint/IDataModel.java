package edu.isnap.hint;

import java.util.List;

import edu.isnap.node.Node;

public interface IDataModel {

	/**
	 * Called for each trace in the dataset.
	 */
	void addTrace(String id, List<Node> trace);
	/**
	 * Called after all traces have been added.
	 */
	void finished();
	/**
	 * Called after all IDataModels have finished initial calculations. Useful if this data model
	 * has dependencies on others, since they will be finished at this point.
	 */
	default void postProcess(HintData data) { }

	/**
	 * Return any DataModels required by this one to operate.
	 */
	default IDataModel[] getDependencies(HintData data) {
		return new IDataModel[0];
	}
}
