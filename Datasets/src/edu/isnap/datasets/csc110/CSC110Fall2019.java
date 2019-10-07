package edu.isnap.datasets.csc110;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;


public class CSC110Fall2019 extends CSC110Dataset {

	public final static Date start = Assignment.date(2019, 8, 15);
	public final static String dataDir = CSC110_BASE_DIR + "/fall2019";
	public final static String dataFile = dataDir + ".csv";
	public final static CSC110Fall2019 instance = new CSC110Fall2019();

	public final static Assignment BrickWall = new ConfigurableAssignment(instance,
			"brickWall", null, true) {
	};
	public final static Assignment DaisyDesign = new ConfigurableAssignment(instance,
			"daisyDesignHW", null, true) {
	};
	public final static Assignment Frogger = new ConfigurableAssignment(instance,
			"frogger", null, true) {
	};
	public final static Assignment GuessingGame1 = new ConfigurableAssignment(instance,
			"guess1Lab", null, true) {
	};
	public final static Assignment GuessingGame = new ConfigurableAssignment(instance,
			"guessingGame", null, true) {
	};
	public final static Assignment GuessingGame2 = new ConfigurableAssignment(instance,
			"guessingGame2", null, true) {
	};
	public final static Assignment Hangman1 = new ConfigurableAssignment(instance,
			"hangman1", null, true) {
	};
	public final static Assignment Hangman3 = new ConfigurableAssignment(instance,
			"hangman3", null, true) {
	};
	public final static Assignment Intro = new ConfigurableAssignment(instance,
			"intro", null, true) {
	};
	public final static Assignment None = new ConfigurableAssignment(instance,
			"none", null, true) {
	};
	public final static Assignment PolygonMakerLab = new ConfigurableAssignment(instance,
			"polygonMakerLab", null, true) {
	};
	public final static Assignment Project1 = new ConfigurableAssignment(instance,
			"project1", null, true) {
	};
	public final static Assignment Squiral = new ConfigurableAssignment(instance,
			"squiral", null, true) {
	};
	public final static Assignment SquiralHW = new ConfigurableAssignment(instance,
			"squiralHW", null, true) {
	};
	public final static Assignment TypingRace = new ConfigurableAssignment(instance,
			"typingRace", null, true) {
	};
	public final static Assignment[] All = {
			BrickWall,
			DaisyDesign,
//			Frogger,
			GuessingGame1,
			GuessingGame,
			GuessingGame2,
			Hangman1,
			Hangman3,
			Intro,
			None,
			PolygonMakerLab,
			Project1,
			Squiral,
			SquiralHW,
			TypingRace,
	};

	private CSC110Fall2019() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

	@Override
	public String getToolInstances() {
		return "iSnap v2.5.2; SourceCheck/Templates v1.4.0";
	}
}