package edu.isnap.hint;

import java.io.Serializable;

import edu.isnap.node.Node;
import edu.isnap.node.Node.NodeConstructor;
import edu.isnap.node.SimpleNode;

public abstract class HintConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	/** If true, uses new version of SourceCheck with more global alignment */
	public boolean sourceCheckV2 = false;
	
	/** If true, uses reference solutions to get high-level hints */
	public boolean useAnnotation = true;

	/**
	 * Should return true if the hint generator can expect traces to keep consistent node IDs
	 * between snapshots, i.e. a node with ID 1 in two snapshots is the same node. If false, AST
	 * diffs will be used to try to infer which nodes are the same between snapshots, which is more
	 * expensive and less accurate.
	 */
	public abstract boolean areNodeIDsConsistent();

	/**
	 * Should return a constructor for the Node to be used with this config.
	 */
	public abstract NodeConstructor getNodeConstructor();
	/**
	 * Should return true if children with this type have no meaningful order
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
	 * Should return true if an Insertion for this node should be nested automatically in
	 * an Insertion for its parent. For example, a BinaryOp may only make sense with its operation
	 * type added, so we can add Add/Sub/Mult/Div/etc automatically.
	 */
	public abstract boolean shouldAutoAdd(Node node);

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
	 * Should return true if the given node's value should be ignored when matching, regardless of
	 * whether it is mapped.
	 */
	public boolean shouldIgnoreNodesValues(Node node) {
		return false;
	}


	/**
	 * Gets a human-readable name for the given Node
	 */
	public String getHumanReadableName(Node node) {
		return "some code";
	}

	/**
	 * When measuring progress towards a goal, nodes in order are given weight multiplied by
	 * this factor compared to nodes that are out of order
	 */
	public int progressOrderFactor = 2;
	/**
	 * When measuring progress towards a goal, nodes in the student's solution but not in the goal
	 * _and_ nodes in the goal solution but not in the student's solution are given negative weight
	 * multiplied by this factor compared to nodes that are out of order
	 */
	public double progressMissingFactor = 0.25;

	/**
	 * If true, the {@link HintConfig#progressMissingFactor} penalty is applied to the descendants
	 * of nodes in a potential solution that have no match in the student's code. Otherwise,
	 * the penalty is only applied to nodes with a matching parent.
	 */
	public boolean penalizeUnmatchedNodeDescendants = true;

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
	 * If true, hints can have subedit hints that should be presented with the parent hint as one
	 * action
	 */
	public boolean createSubedits = true;

	// TODO: These two are temporarily configurable and a default should probably be chosen
	/**
	 * If true, any single line that has a single insertion and deletion will combine those
	 * into one replacement.
	 */
	public boolean createSingleLineReplacements = false;

	/**
	 * If true, the distance measure is used to determine child alignment subcost for tie breaking;
	 * otherwise, a special measure is used which has no cost for deletions and does not count
	 * valueless nodes.
	 */
	public boolean useDeletionsInSubcost = true;

	/**
	 * Determines how Node values will be used when matching and hinting nodes
	 */
	public ValuesPolicy valuesPolicy = ValuesPolicy.MatchAllWithMapping;
	public enum ValuesPolicy {
		/** All values are ignored, and only types are used for matching */
		IgnoreAll,
		/**
		 * Only values in the {@link HintConfig#getValueMappedTypes()} list are used in matching.
		 * Other values are ignored, with types used instead.
		 */
		MappedOnly,
		/**
		 * All values are used when matching nodes, and a mapping is used for nodes in the
		 *  {@link HintConfig#getValueMappedTypes()} list.
		 */
		MatchAllWithMapping,
		/** All values are used exactly when matching nodes, and no mapping is used */
		MatchAllExactly,
	}

	public final boolean shouldCalculateValueMapping() {
		return valuesPolicy == ValuesPolicy.MappedOnly ||
				valuesPolicy == ValuesPolicy.MatchAllWithMapping;
	}

	/**
	 * If set to true, when a target solution contains a mapped value with no pair in the student's
	 * code, we suggest the target solution's value. So for example, rather than suggesting to
	 * add "a variable," we would suggest adding "variable x".
	 */
	public boolean suggestNewMappedValues = false;

	/**
	 * If true, a HintMap build from this assignment will only include graded submissions.
	 */
	public boolean requireGrade = false;
	/**
	 * If true, these solutions came from students and should be preprocessed to remove side-scripts
	 */
	public boolean preprocessSolutions = true;
	/**
	 * If true, hint priorities will be calculated and used to prioritize more important hints
	 */
	public boolean usePriority = true;
	/**
	 * The k used when assigning a priority using k-nearest neighbors
	 */
	public int votingK = 10;

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

		@Override
		public boolean shouldAutoAdd(Node node) {
			return false;
		}

		@Override
		public NodeConstructor getNodeConstructor() {
			return SimpleNode::new;
		}

		@Override
		public boolean areNodeIDsConsistent() {
			return false;
		}

	}
}
