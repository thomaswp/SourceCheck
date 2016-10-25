package com.snap.graph;

import java.util.List;

import com.snap.data.Canonicalization;
import com.snap.data.Code;
import com.snap.data.Code.Accumulator;
import com.snap.data.IHasID;
import com.snap.graph.data.Node;

public class SimpleNodeBuilder {

	public static Node toTree(Code code, final boolean canon) {
		return toTree(code, canon, null);
	}

	private static Node toTree(Code code, final boolean canon, Node parent) {
		String id = code instanceof IHasID ? ((IHasID) code).getID() : null;
		final Node node = new Node(parent, code.name(canon), id);
		node.tag = code;
		code.addChildren(canon, new Accumulator() {

			void add(String code) {
				node.children.add(new Node(node, code));
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
					Node child = toTree(code, canon, node);
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
					node.canonicalizations.add(new com.snap.graph.data.Canonicalization.InvertOp(
							((Canonicalization.InvertOp) canon).name));
				} else if (canon instanceof Canonicalization.Rename) {
					node.canonicalizations.add(new com.snap.graph.data.Canonicalization.Rename(
							((Canonicalization.Rename) canon).name));
				} else if (canon instanceof Canonicalization.SwapArgs) {
					node.canonicalizations.add(new com.snap.graph.data.Canonicalization.SwapArgs());
				} else {
					throw new RuntimeException("Unknown canonicalization");
				}
			}
		});

		return node;
	}
}
