package edu.isnap.datasets.csc110;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;


public class CSC110Fall2019 extends CSC110Dataset {

	public final static Date start = Assignment.date(2019, 8, 15);
	public final static String dataDir = CSC110_BASE_DIR + "/fall2019";
	public final static String dataFile = dataDir + ".csv";
	public final static CSC110Fall2019 instance = new CSC110Fall2019();

	public final static Assignment None = new ConfigurableAssignment(instance,
			"none", null, true) {
	};

	public final static Assignment Intro = new ConfigurableAssignment(instance,
			"intro", null, true) {
	};

	public final static Assignment PolygonMakerLab = new ConfigurableAssignment(instance,
			"polygonMakerLab", null, true) {
	};

	public final static Assignment SquiralHW = new ConfigurableAssignment(instance,
			"squiralHW", null, true) {
	};

	public final static Assignment DaisyDesign = new ConfigurableAssignment(instance,
			"daisyDesignHW", null, true, true, SquiralHW) {

		@Override
		public Assignment getLocationAssignment(String attemptID) {
			switch (attemptID) {
			case "13e1ac22-1a7d-47d9-87f4-00d0c4aaa158":
			case "4181d740-dd01-487c-af8d-1fd0b5f21353":
			case "a34c2bd8-723d-4d4b-8eb0-f7b94669e9a5":
			case "bfcbfcc6-3815-4aba-a543-fd09fc89b5c7":
			case "b158f447-455a-4834-a7b2-0ee4ee52ec94":
			case "c2e07169-d1cc-4244-9fc8-80ed0356c6a4":
				return PolygonMakerLab;
			case "352bdbcf-62d0-4aa6-ab37-9be62e5a0e2b":
				return Intro;
			}
			return super.getLocationAssignment(attemptID);
		};
	};

	public final static Assignment GuessingGame = new ConfigurableAssignment(instance,
			"guessingGame", null, true) {
	};

	public final static Assignment BrickWall = new ConfigurableAssignment(instance,
			"brickWall", null, true) {
	};

	public final static Assignment Frogger = new ConfigurableAssignment(instance,
			"frogger", null, true) {
	};

	public final static Assignment Hangman1 = new ConfigurableAssignment(instance,
			"hangman1", null, true) {
	};

	public final static Assignment Hangman3 = new ConfigurableAssignment(instance,
			"hangman3", null, true) {
	};

	public final static Assignment GuessingGame2 = new ConfigurableAssignment(instance,
			"guessingGame2", null, true) {
	};

	public final static Assignment TypingRace = new ConfigurableAssignment(instance,
			"typingRace", null, true) {
	};

	public final static Assignment Project1 = new ConfigurableAssignment(instance,
			"project1", null, true) {
	};

	public final static Assignment[] All = {
			BrickWall,
			DaisyDesign,
//			Frogger,
			GuessingGame,
			GuessingGame2,
			Hangman1,
			Hangman3,
			Intro,
			None,
			PolygonMakerLab,
			Project1,
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