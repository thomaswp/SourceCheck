package edu.isnap.rating;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.isnap.ctd.graph.ASTNode;

public interface RatingConfig {

	/**
	 * Should return true if hint comparisons should consider the value of numeric literals when
	 * determining if two hint states are equivalent. Textual literal values are always ignored.
	 */
	public boolean useSpecificNumericLiterals();

	/**
	 * Should return true if this node can be safely pruned if it has no children, e.g. scripts with
	 * no body.
	 */
	public boolean trimIfChildless(String type);

	/**
	 * Should return true if this node can be safely pruned if its parent is newly added, e.g. in
	 * Snap, some nodes are added automatically to parents and be safely ignored. This only occurs
	 * if all children of the node in question can also be trimmed.
	 */
	public boolean trimIfParentIsAdded(String type, String value);

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
	 * If a node with the given type should have its value normalized in some way (e.g. rounding
	 * numbers or modifying procedure names) before comparison, this method should return that
	 * normalized value. Otherwise, it should return the given value.
	 */
	public String normalizeNodeValue(String type, String value);

	public final static RatingConfig Default = new RatingConfig() {

		@Override
		public boolean useSpecificNumericLiterals() {
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
		public boolean trimIfParentIsAdded(String type, String value) {
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

		@Override
		public String normalizeNodeValue(String type, String value) {
			return value;
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
		public boolean trimIfChildless(String type) {
			return "script".equals(type);
		}

		@Override
		public boolean trimIfParentIsAdded(String type, String value) {
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
		public String normalizeNodeValue(String type, String value) {
			if (value == null) return value;
			if ("customBlock".equals(type) || "evaluateCustomBlock".equals(type)) {
				// Custom block names shouldn't change with the number of parameters, since this
				// is just a reflection of other code changes, which will be compared elsewhere
				return value.replace(" %s", "");
			}
			return value;
		}
	};

	public static RatingConfig Python = new RatingConfig() {
		@Override
		public boolean useSpecificNumericLiterals() {
			return true;
		}

		// These nodes are added automatically (i.e. if you add a FunctionDef, arguments are added),
		// and they have no meaning if they have no children that aren't on this list
		private final Set<String> Prunable = new HashSet<>(Arrays.asList(
				new String[] {
						ASTNode.EMPTY_TYPE,
						"Load",
						"Store",
						"Del",
						"list",
						"alias",
						"arguments",
						"arg",
						"Expr",
				}
			));

		@Override
		public boolean trimIfParentIsAdded(String type, String value) {
			return Prunable.contains(type) ||
					// Names without a value area newly created identifier not previously present.
					// Since there was no way in Python to create a variable assignment without
					// specifying a variable, these should not be required for exact matching,
					// similar to other nodes that are automatically inserted as children
					("Name".equals(type) && value == null);
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

		@Override
		public String normalizeNodeValue(String type, String value) {
			return value;
		}
	};
}