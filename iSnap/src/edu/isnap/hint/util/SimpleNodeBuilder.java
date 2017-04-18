package edu.isnap.hint.util;

import java.util.List;

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
		final Node node = new Node(parent, code.name(canon), id);
		node.tag = code;
		code.addChildren(canon, new Accumulator() {

			void add(String code) {
				node.children.add(new Node(node, code, ider.getID(code, node)));
			}

			@Override
			public void add(Iterable<? extends Code> codes) {
				for (Code code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Code code) {
				if (code == null) {
					add("null");
				} else {
					Node child = toTree(code, canon, node, ider);
					node.children.add(child);
				}
			}

			@Override
			public void addVariables(List<String> codes) {
				for (String code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Canonicalization canon) {
				if (canon instanceof Canonicalization.InvertOp) {
					node.canonicalizations.add(new edu.isnap.ctd.hint.Canonicalization.InvertOp(
							((Canonicalization.InvertOp) canon).name));
				} else if (canon instanceof Canonicalization.Rename) {
					node.canonicalizations.add(new edu.isnap.ctd.hint.Canonicalization.Rename(
							((Canonicalization.Rename) canon).name));
				} else if (canon instanceof Canonicalization.SwapArgs) {
					node.canonicalizations.add(new edu.isnap.ctd.hint.Canonicalization.SwapArgs());
				} else {
					throw new RuntimeException("Unknown canonicalization");
				}
			}
		});

		return node;
	}

	public interface IDer {
		String getID(Code code, Node parent);
		String getID(String code, Node parent);
	}

	private static IDer DefaultIDer = new IDer() {
		@Override
		public String getID(Code code, Node parent) {
			return code instanceof IHasID ? ((IHasID) code).getID() : null;
		}

		@Override
		public String getID(String code, Node parent) {
			return null;
		}
	};
}
