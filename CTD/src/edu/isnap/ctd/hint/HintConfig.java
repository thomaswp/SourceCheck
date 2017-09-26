package edu.isnap.ctd.hint;

import java.util.HashSet;

import edu.isnap.ctd.graph.Node;

public class HintConfig {
	/**
	 * If true, a HintMap build from this assignment will only include graded submissions.
	 */
	public boolean requireGrade = false;
	/**
	 * If true, these solutions came from students and should be preprocessed to remove side-scripts
	 */
	public boolean preprocessSolutions = true;
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
	 * When measuring progress towards a goal, nodes in the student's solution but not in the goal
	 * solution are given negative weight multiplied by this factor compared to nodes that are
	 * out of order
	 */
	public double progressMissingFactor = 0.25;
	/**
	 * If true, infers decision rules about correct solutions and uses these to filter solutions
	 * based on the decisions that the hint-requesting student has made.
	 */
	public boolean useRulesToFilter = true;
	/**
	 * The minimum number of solutions to return when filtering using decision rules.
	 */
	public int minRuleFilterSolutions = 5;
	/**
	 * The minimum support for extracted decision rules.
	 */
	public double ruleSupportThreshold = 0.05;
	/**
	 * Use values when matching nodes, as defined by {@link HintConfig#valueMappedTypes}
	 */
	public boolean useValues = false;

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

	public final HashSet<String> haveSideScripts = new HashSet<>();
	{
		for (String c : new String[] {
				"stage",
				"sprite",
		}) {
			haveSideScripts.add(c);
		}
	}

	/** Nodes that should be self-contained, and their children should not be inter-matched */
	public final HashSet<String> containers = new HashSet<>();
	{
		for (String c : new String[] {
				"sprite",
		}) {
			containers.add(c);
		}
	}

	public final HashSet<String> harmlessCalls = new HashSet<>();
	{
		for (String c : new String[] {
				"receiveGo",
				"setColor",
				"setHue",
				"clear",
				"receiveKey",
				"receiveInteraction",
		}) {
			harmlessCalls.add(c);
		}
	}

	/**
	 * Types that should have their values mapped when matching nodes, e.g. variable nodes.
	 * Each type in the inner array will be mapped together, (e.g. function declarations and calls)
	 */
	public String[][] valueMappedTypes = {
			new String[] { "var" },
			new String[] { "customBlock", "evaluateCustomBlock" },
	};

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

	public boolean isCodeElement(Node node) {
		return node != null && !node.hasType(script) &&
				node.hasAncestor(new Node.TypePredicate(script));
	}
}
