package edu.isnap.hint.util;

import edu.isnap.ctd.graph.Node;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Code.Accumulator;
import edu.isnap.parser.elements.util.Canonicalization;
import edu.isnap.parser.elements.util.IHasID;

public class SimpleNodeBuilder {

	public static Node toTree(Code code, final boolean canon) {
		return toTree(code, canon, null, DefaultIDer);
	}

	public static Node toTree(Code code, final boolean canon, IDer ider) {
		return toTree(code, canon, null, ider);
	}

	private static Node toTree(Code code, final boolean canon, Node parent, final IDer ider) {
		String id = ider.getID(code, parent);
		final Node node = new SnapNode(parent, code.type(canon), code.value(), id);
		node.tag = code;
		code.addChildren(canon, new Accumulator() {

			@Override
			public void add(Iterable<? extends Code> codes) {
				for (Code code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Code code) {
				if (code == null) {
					add("null", null);
				} else {
					Node child = toTree(code, canon, node, ider);
					node.children.add(child);
				}
			}

			@Override
			public void add(String type, String value) {
				node.children.add(new SnapNode(node, type, value, ider.getID(type, node)));
			}

			@Override
			public void add(Canonicalization canon) {
				if (canon instanceof Canonicalization.Rename) {
					node.canonicalizations.add(new edu.isnap.ctd.hint.Canonicalization.Rename(
							((Canonicalization.Rename) canon).name));
				} else if (canon instanceof Canonicalization.SwapBinaryArgs) {
					node.canonicalizations.add(
							new edu.isnap.ctd.hint.Canonicalization.SwapBinaryArgs());
				} else if (canon instanceof Canonicalization.Reorder) {
					node.canonicalizations.add(
							new edu.isnap.ctd.hint.Canonicalization.Reorder(
									((Canonicalization.Reorder) canon).reordering));
				} else {
					throw new RuntimeException("Unknown canonicalization");
				}
			}
		});

		// If this is a symmetric node, its arguments have no order
		// Order groups not yet supported for arguments
//		if (CallBlock.SYMMETRIC.contains(node.type())) {
//			for (Node child : node.children) {
//				child.setOrderGroup(1);
//			}
//		}

		return node;
	}

	public interface IDer {
		String getID(Code code, Node parent);
		String getID(String code, Node parent);
	}

	public final static IDer DefaultIDer = new IDer() {
		@Override
		public String getID(Code code, Node parent) {
			return code instanceof IHasID ? ((IHasID) code).getID() : null;
		}

		@Override
		public String getID(String code, Node parent) {
			return null;
		}
	};

	/**
	 * Ensures that every node will have a unique ID, defaulting to the getID() value, but using an
	 * incremented value if not. This is not appropriate for comparing snapshots across time, as the
	 * dynamic IDs of nodes will change from snapshot to snapshot
	 */
	public static class CompleteDynamicIDer implements IDer {

		int next = 0;

		@Override
		public String getID(Code code, Node parent) {
			String id = code instanceof IHasID ? ((IHasID) code).getID() : null;
			if (id == null) id = getID(code.type(), parent);
			return id;
		}

		@Override
		public String getID(String code, Node parent) {
			return "ID" + next++;
		}
	};
}
