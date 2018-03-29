package edu.isnap.datasets.aggregate;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.Fall2015;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.Spring2016;
import edu.isnap.datasets.Spring2017;

public class CSC200 extends AggregateDataset {

	public final static CSC200 instance = new CSC200();
	public final static String dataDir = "../data/csc200/all";

	public CSC200() {
		super(dataDir, Fall2015.instance, Spring2016.instance, Fall2016.instance,
				Spring2017.instance, Fall2017.instance);
		// Currently not including Spring 2018 until we decide how to count the new Squiral and GG2
	}

	@Override
	protected HintConfig getDefaultHintConfig() {
		HintConfig config = super.getDefaultHintConfig();
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
