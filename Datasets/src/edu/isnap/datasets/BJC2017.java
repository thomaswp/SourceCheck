package edu.isnap.datasets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.csv.CSVFormat;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;

public class BJC2017 extends Dataset {

	public final static Date start = null;
	public final static String dataDir = "../data/bjc/bjc2017";
	public final static String dataFile = dataDir + ".csv";
	public final static BJC2017 instance = new BJC2017();

	private static class BJCAssignment extends ConfigurableAssignment {

		public BJCAssignment(String name) {
			super(instance, name, null, false);
		}

		@Override
		public HintConfig getConfig() {
			HintConfig config = new HintConfig();
			return config;
		}

	}

	@Override
	public CSVFormat dataFileCSVFormat() {
		return super.dataFileCSVFormat().withEscape('\\');
	}

	public final static Assignment U1_L1_Alonzo = new BJCAssignment("U1_L1_Alonzo");

	public static final Assignment U1_L2_Gossip = new BJCAssignment("U1_L2_Gossip");

	public static final Assignment U1_L2_P4_GreetPlayer = new BJCAssignment("U1_L2_P4_GreetPlayer");

	public static final Assignment U1_L3_P1_Experiments = new BJCAssignment("U1_L3_P1_Experiments");

	public static final Assignment U1_L3_Pinwheel = new BJCAssignment("U1_L3_Pinwheel");

	public static final Assignment U1_L3_P6_Looping = new BJCAssignment("U1_L3_P6_Looping");

	public final static Assignment U1_L3_P7_Graphics = new BJCAssignment("U1_L3_P7_Graphics");

	public final static Assignment U1_P3_Pong = new BJCAssignment("U1_P3_Pong");

	public final static Assignment U2_L1_GuessingGame = new BJCAssignment("U2_L1_GuessingGame");

	public final static Assignment U2_L1_P3_Alonzo = new BJCAssignment("U2_L1_P3_Alonzo");

	public final static Assignment U2_L3_Predicates = new BJCAssignment("U2_L3_Predicates");

	public final static Assignment U2_L3_P2_KeepingData = new BJCAssignment("U2_L3_P2_KeepingData");

	public final static Assignment U2_L3_P3_WordPuzzleSolver =
			new BJCAssignment("U2_L3_P3_WordPuzzleSolver");

	public final static Assignment U2_L4_BrickWall = new BJCAssignment("U2_L4_BrickWall");

	public final static Assignment[] Pre_PD_Work = {
			U1_L1_Alonzo,
			U1_L2_Gossip,
			U1_L2_P4_GreetPlayer,
			U1_L3_P1_Experiments,
			U1_L3_Pinwheel,
			U1_L3_P6_Looping,
			U1_L3_P7_Graphics,
			U1_P3_Pong,
			U2_L1_GuessingGame,
			U2_L1_P3_Alonzo,
			U2_L3_Predicates,
			U2_L3_P2_KeepingData,
			U2_L3_P3_WordPuzzleSolver,
			U2_L4_BrickWall,
	};

	public final static Assignment U1_L3_P6_NestSquares = new BJCAssignment("U1_L3_P6_NestSquares");

	public final static Assignment A1_eCard = new BJCAssignment("A1_eCard");

	public final static Assignment U3_L1_ContactList = new BJCAssignment("U3_L1_ContactList");

	public final static Assignment U5_L1_Search = new BJCAssignment("U5_L1_Search");

	public final static Assignment U5_L1_P2_ImprovedSearch =
			new BJCAssignment("U5_L1_P2_ImprovedSearch");

	public final static Assignment U3_L2_P3_Sorting = new BJCAssignment("U3_L2_P3_Sorting");

	public final static Assignment U5_L2_Models = new BJCAssignment("U5_L2_Models");

	public final static Assignment U5_L3_P3_DiseaseSpread =
			new BJCAssignment("U5_L3_P3_DiseaseSpread");

	public final static Assignment A2_ShoppingList = new BJCAssignment("A2_ShoppingList");

	public final static Assignment U3_L5_P1_Graphs = new BJCAssignment("U3_L5_P1_Graphs");

	public final static Assignment P1_Create = new BJCAssignment("P1_Create");

	public final static Assignment[] F2F_Work = {
			U1_L3_P6_NestSquares,
			A1_eCard,
			U3_L1_ContactList,
			U5_L1_Search,
			U5_L1_P2_ImprovedSearch,
			U3_L2_P3_Sorting,
			U5_L2_Models,
			U5_L3_P3_DiseaseSpread,
			A2_ShoppingList,
			U3_L5_P1_Graphs,
			P1_Create,
	};

	public final static Assignment[] All;
	static {
		List<Assignment> list = new ArrayList<>();
		list.addAll(Arrays.asList(Pre_PD_Work));
		list.addAll(Arrays.asList(F2F_Work));
		All = list.toArray(new Assignment[list.size()]);
	}

	private BJC2017() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}
}