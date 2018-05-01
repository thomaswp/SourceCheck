package edu.isnap.rating;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.rating.TutorHint.Validity;

public interface RatingConfig {

	/**
	 * Should return true if hint comparisons should consider the value of numeric literals when
	 * determining if two hint states are equivalent. Textual literal values are always ignored.
	 */
	public boolean useSpecificNumericLiterals();

	@Deprecated
	public boolean areNodeIDsConsistent();

	/**
	 * Should return true if this node can be safely pruned if it has no children, e.g. scripts with
	 * no body.
	 */
	public boolean trimIfChildless(String type);

	/**
	 * Should return true if this node can be safely pruned if its parent is newly added, e.g. in
	 * Snap, some nodes are added automatically to parents and be safely ignored.
	 */
	public boolean trimIfParentIsAdded(String type);

	/**
	 * Should return true if nodes of this type have a fixed number of children as opposed to a
	 * flexible number. This is used when extracting the edits from a given hint to determine at
	 * which index a node was inserted. For nodes with a flexible number of children, some logic
	 * is used to infer the index of a newly inserted node in the original AST.
	 */
	public boolean hasFixedChildren(String type, String parentType);

	/**
	 *  Should return true if nodes of this type have "bodies," which should be printed on multiple
	 *  lines. Note: this method should only be used for printing.
	 */
	public boolean nodeTypeHasBody(String type);

	/**
	 * Should return the highest {@link Validity} of {@link TutorHint} that should be assumed
	 * present for any {@link HintRequest}. Any HintRequest with out a TutorHint with at least this
	 * validity is assumed to have no valid hints (e.g. the code is correct), and it will be skipped
	 * in hint rating analysis.
	 */
	public default Validity highestRequiredValidity() {
		return Validity.MultipleTutors;
	}

	public final static RatingConfig Default = new RatingConfig() {

		@Override
		public boolean useSpecificNumericLiterals() {
			return false;
		}

		@Override
		public boolean areNodeIDsConsistent() {
			return false;
		}

		private final Set<String> Prunable = new HashSet<>(Arrays.asList(
			new String[] {
					"Expr",
			}
		));

		@Override
		public boolean trimIfChildless(String type) {
			return Prunable.contains(type);
		}

		@Override
		public boolean trimIfParentIsAdded(String type) {
			return false;
		}

		@Override
		public boolean nodeTypeHasBody(String type) {
			return false;
		}

		@Override
		public boolean hasFixedChildren(String type, String parentType) {
			return false;
		}
	};

	public final static RatingConfig Snap = new RatingConfig() {

		private final Set<String> BodyTypes = new HashSet<>(Arrays.asList(
			new String[] {
					"Snap!shot",
					"snapshot",
					"stage",
					"sprite",
					"script",
					"customBlock",
			}
		));

		private final Set<String> Prunable = new HashSet<>(Arrays.asList(
			new String[] {
					ASTNode.EMPTY_TYPE,
					"literal",
					"script",
					"list",
					"varMenu",
			}
		));

		@Override
		public boolean useSpecificNumericLiterals() {
			return false;
		}

		@Override
		public boolean areNodeIDsConsistent() {
			return true;
		}

		@Override
		public boolean trimIfChildless(String type) {
			return "script".equals(type);
		}

		@Override
		public boolean trimIfParentIsAdded(String type) {
			return Prunable.contains(type);
		}

		@Override
		public boolean nodeTypeHasBody(String type) {
			return BodyTypes.contains(type);
		}

		// Note: we exclude list and reify blocks here, since (for now) we want to treat them as
		// having fixed children
		private final HashSet<String> haveFlexibleChildren = new HashSet<>(Arrays.asList(
				new String[] {
						"Snap!shot",
						"snapshot",
						"stage",
						"sprite",
						"script",
						"customBlock",
				}
		));

		@Override
		public boolean hasFixedChildren(String type, String parentType) {
			return !haveFlexibleChildren.contains(type);
		}

		@Override
		public Validity highestRequiredValidity() {
			return Validity.Consensus;
		}
	};

	public static RatingConfig Python = new RatingConfig() {
		@Override
		public boolean useSpecificNumericLiterals() {
			return true;
		}

		@Override
		public boolean areNodeIDsConsistent() {
			return false;
		}

		private final Set<String> Prunable = new HashSet<>(Arrays.asList(
				new String[] {
						ASTNode.EMPTY_TYPE,
						"Load",
						"Store",
						"Del",
				}
			));

		@Override
		public boolean trimIfParentIsAdded(String type) {
			return Prunable.contains(type);
		}

		@Override
		public boolean trimIfChildless(String type) {
			return "list".equals(type);
		}

		@Override
		public boolean nodeTypeHasBody(String type) {
			return "list".equals(type);
		}

		// Some nodes in python have list children that almost always have a single child, such as
		// comparison and assignment operators. This allows the children of these to be replaced, as
		// fixed arguments, rather than added and removed separately as statements.
		private final HashSet<String> usuallySingleListParents = new HashSet<>(Arrays.asList(
				new String[] {
					"Compare",
					"Assign",
				}
		));

		@Override
		public boolean hasFixedChildren(String type, String parentType) {
			// In Python, only the list type has flexible children, and even some of those are
			// almost always fixed (at least for simple student programs)
			return !"list".equals(type) || usuallySingleListParents.contains(parentType);
		}
	};
}