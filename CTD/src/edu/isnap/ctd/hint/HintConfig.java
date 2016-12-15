package edu.isnap.ctd.hint;

import java.util.HashSet;

public class HintConfig {
	/**
	 * When at least this proportion of visitors to a state finished there,
	 * we flag hints to leave it with caution
	 */
	public double stayProportion = 0.67;
	/** We prune out states with weight less than this */
	public int pruneNodes = 2;
	/** We prune out goals with fewer students than this finishing there */
	public int pruneGoals = 2;
	/**
	 * To hint towards a nearby neighbor, it must be less than this distance from the student's
	 * current state
	 */
	public int maxNN = 3;
	/** We add synthetic edges between nodes with distance no more than this */
	public int maxEdgeAddDistance = 1;
	/** We prune edges between states with distance greater than this */
	public int maxEdgeDistance = 2;
	/** The maximum number of siblings to look at at either end when considering context */
	public int maxContextSiblings = 3;
	/** Ratio of unused to used blocks in a side-script for it to used in  a LinkHint */
	public int linkUsefulRatio = 2;
	/** Type of script nodes, which should use code hinting */
	public String script = "script";
	/** Type of literals which are automatically inserted in parameter slots*/
	public String literal = "literal";
	/**
	 * When measuring progress towards a goal, nodes in order are given weight multiplied by
	 * this factor compared to nodes that are out of order
	 */
	public int progressOrderFactor = 2;

	/**
	 * Code elements that have exactly one script child or unordered children and therefore should
	 * not have their children used as context
	 */
	public final HashSet<String> badContext = new HashSet<>();
	{
		for (String c : new String[] {
				// These control structures hold exactly one script
				"doIf",
				"doUntil",
				// Sprites' children are unordered
				"sprite",
				// Custom block inputs aren't usually added up front, so they're more distracting
				// than helpful, and block creation order has more bearing on target goal
				"customBlock",
		}) {
			badContext.add(c);
		}
	}

	/**
	 * When we have hints for these parent blocks, we should go straight to the goal, since there's
	 * no point in, e.g., leading them through adding one variable, then another. These are the
	 * "structure hints" on the client side
	 */
	public final HashSet<String> straightToGoal = new HashSet<>();
	{
		for (String c : new String[] {
				"snapshot",
				"stage",
				"sprite",
				"customBlock",
		}) {
			straightToGoal.add(c);
		}
	}

	/**
	 * Power used in the distance weight formula. Higher values penalize higher distances faster.
	 * Must be >= 1.
	 */
	public int distanceWeightPower = 2;
	/**
	 * Base used in the distance weight formula. Higher values penalize higher distances slower.
	 * Must be > 0.
	 */
	public double distanceWeightBase = 0.5;

	public double getDistanceWeight(double distance) {
		// TODO: See if you can find a way to may this discrete instead
		return Math.pow(distanceWeightBase, distanceWeightPower) /
				Math.pow(distanceWeightBase + distance, distanceWeightPower); // max 1
	}
}
