package edu.isnap.datasets.aggregate;

import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.Spring2016;
import edu.isnap.hint.SnapHintConfig;

public class HintEvalData extends AggregateDataset {
	public final static HintEvalData instance = new HintEvalData();
	public final static String dataDir = "../data/csc200/hintEvalData";

	public HintEvalData() {
		super(dataDir, Spring2016.instance, Fall2017.instance);
	}

	@Override
	protected SnapHintConfig getDefaultHintConfig() {
		SnapHintConfig config = super.getDefaultHintConfig();
		config.requireGrade = true;
		return config;
	}

	public final static Assignment LightsCameraAction =
			createAssignment(instance, "lightsCameraActionHW");
	public final static Assignment PolygonMaker =
			createAssignment(instance, "polygonMakerLab");
	public final static Assignment Squiral =
			createAssignment(instance, "squiralHW");
	public final static Assignment GuessingGame1 =
			createAssignment(instance, "guess1Lab");
	public final static Assignment GuessingGame2 =
			createAssignment(instance, "guess2HW");
	public final static Assignment GuessingGame3 =
			createAssignment(instance, "guess3Lab");

	public final static Assignment[] ALL = {
			LightsCameraAction,
			PolygonMaker,
			Squiral,
			GuessingGame1,
			GuessingGame2,
			GuessingGame3,
	};

	@Override
	public Assignment[] all() {
		return ALL;
	}
}
