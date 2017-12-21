package edu.isnap.rating;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface RatingConfig {
	public boolean useSpecificNumericLiterals();
	public boolean areNodeIDsConsistent();
	public boolean trimIfChildless(String type);
	public boolean trimIfParentIsAdded(String type);
	/**
	 *  Should return true if nodes of this type have "bodies," which should be printed on multiple
	 *  lines. Note: this method should only be used for printing.
	 */
	public boolean nodeTypeHasBody(String type);

	public final static RatingConfig Default = new RatingConfig() {

		@Override
		public boolean useSpecificNumericLiterals() {
			return false;
		}

		@Override
		public boolean areNodeIDsConsistent() {
			return false;
		}

		@Override
		public boolean trimIfChildless(String type) {
			return false;
		}

		@Override
		public boolean trimIfParentIsAdded(String type) {
			return false;
		}

		@Override
		public boolean nodeTypeHasBody(String type) {
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

		@Override
		public boolean trimIfParentIsAdded(String type) {
			return false;
		}

		@Override
		public boolean trimIfChildless(String type) {
			return false;
		}

		@Override
		public boolean nodeTypeHasBody(String type) {
			return "list".equals(type);
		}
	};
}