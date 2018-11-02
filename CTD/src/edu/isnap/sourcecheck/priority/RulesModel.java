package edu.isnap.sourcecheck.priority;

import java.util.List;

import edu.isnap.hint.HintData;
import edu.isnap.hint.IDataModel;
import edu.isnap.hint.SolutionsModel;
import edu.isnap.node.Node;

public class RulesModel implements IDataModel {

	private RuleSet ruleSet;

	@Override
	public IDataModel[] getDependencies(HintData data) {
		return new IDataModel[] {
				 new SolutionsModel(),
		};
	}

	public RuleSet getRuleSet() {
		return ruleSet;
	}

	@Override
	public void addTrace(String id, List<Node> trace) {
	}

	@Override
	public void finished() {
	}

	@Override
	public void postProcess(HintData data) {
		ruleSet = new RuleSet(data.getData(SolutionsModel.class).getSolutions(), data.config);
	}
}
