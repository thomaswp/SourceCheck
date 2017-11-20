package edu.isnap.ctd.hint;

import java.io.Serializable;

import edu.isnap.ctd.graph.Node;

public abstract class HintConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Should return true if children of this type have no meaningful order
	 */
	public abstract boolean isOrderInvariant(String type);

	/**
	 * Should return true if this node has a fixed number of children, so insertions should replace
	 * existing elements.
	 */
	public abstract boolean hasFixedChildren(Node node);

	/**
	 * Should return true if this node can be moved and reordered.
	 */
	public abstract boolean canMove(Node node);

	/**
	 * Should return true if this node is self-contained, and its children should not be
	 * inter-matched
	 */
	public abstract boolean isContainer(String type);

	/**
	 * Types that should have their values mapped when matching nodes, e.g. variable nodes.
	 * Each type in the inner array will be mapped together, (e.g. function declarations and calls)
	 */
	public abstract String[][] getValueMappedTypes();

	/**
	 * Should return true if matching nodes of this type should not be valued when comparing ASTs.
	 */
	public boolean isValueless(String type) {
		return false;
	}

	/**
	 * Should return true if this node can have children that are "side scripts," which can be
	 * pruned if {@link HintConfig#preprocessSolutions} is true.
	 */
	public boolean hasSideScripts(String type) {
		return false;
	}

	/**
	 * Should return true if nodes of this type are "harmless" and should not be deleted.
	 */
	public boolean isHarmlessType(String type) {
		return false;
	}

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
	public boolean useValues = true;

	/**
	 * If true, a HintMap build from this assignment will only include graded submissions.
	 */
	public boolean requireGrade = false;
	/**
	 * If true, these solutions came from students and should be preprocessed to remove side-scripts
	 */
	public boolean preprocessSolutions = true;

	// BEGIN old CDT config attributes
	// TODO: Remove these elements and clean up old CTD algorithm

	/**
	 *  Should return true if this node has code statements as children
	 */
	@Deprecated
	public boolean isScript(String type) {
		return false;
	}

	/**
	 * Should return true if the children of this node do not provide context for where or whether
	 * it should be used.
	 */
	@Deprecated
	public boolean isBadContext(String type) {
		return false;
	}

	/**
	 * Should return true if this
	 */
	@Deprecated
	public boolean shouldGoStraightToGoal(String type) {
		return false;
	}
	/**
	 * When at least this proportion of visitors to a state finished there,
	 * we flag hints to leave it with caution
	 */
	@Deprecated
	public double stayProportion = 0.67;
	/** We prune out states with weight less than this */
	@Deprecated
	public int pruneNodes = 2;
	/** We prune out goals with fewer students than this finishing there */
	@Deprecated
	public int pruneGoals = 2;
	/**
	 * To hint towards a nearby neighbor, it must be less than this distance from the student's
	 * current state
	 */
	@Deprecated
	public int maxNN = 3;
	/** We add synthetic edges between nodes with distance no more than this */
	@Deprecated
	public int maxEdgeAddDistance = 1;
	/** We prune edges between states with distance greater than this */
	@Deprecated
	public int maxEdgeDistance = 2;
	/** The maximum number of siblings to look at at either end when considering context */
	@Deprecated
	public int maxContextSiblings = 3;
	/** Ratio of unused to used blocks in a side-script for it to used in  a LinkHint */
	@Deprecated
	public int linkUsefulRatio = 2;

	/**
	 * Power used in the distance weight formula. Higher values penalize higher distances faster.
	 * Must be >= 1.
	 */
	@Deprecated
	public int distanceWeightPower = 2;

	/**
	 * Base used in the distance weight formula. Higher values penalize higher distances slower.
	 * Must be > 0.
	 */
	@Deprecated
	public double distanceWeightBase = 0.5;

	@Deprecated
	public double getDistanceWeight(double distance) {
		// TODO: See if you can find a way to may this discrete instead
		return Math.pow(distanceWeightBase, distanceWeightPower) /
				Math.pow(distanceWeightBase + distance, distanceWeightPower); // max 1
	}

	public static class SimpleHintConfig extends HintConfig {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isScript(String type) {
			return false;
		}

		@Override
		public boolean isContainer(String type) {
			return false;
		}

		@Override
		public String[][] getValueMappedTypes() {
			return new String[][] {};
		}

		@Override
		public boolean isOrderInvariant(String type) {
			return false;
		}

		@Override
		public boolean hasFixedChildren(Node node) {
			return false;
		}

		@Override
		public boolean canMove(Node node) {
			return false;
		}

	}
}
