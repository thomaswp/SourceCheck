package com.snap.graph;

import java.util.List;

import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Code.Accumulator;
import edu.isnap.parser.elements.util.Canonicalization;
import util.LblTree;

public class SimpleTreeBuilder {

	public static LblTree toTree(Code code, final int id, final boolean canon) {
		final LblTree tree = new LblTree(null, id);
		tree.setLabel(code.name(canon));
		code.addChildren(canon, new Accumulator() {

			void add(String code) {
				tree.add(new LblTree(code, id));
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
					LblTree child = toTree(code, id, canon);
					tree.add(child);
				}
			}

			@Override
			public void addVariables(List<String> codes) {
				for (String code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Canonicalization canon) { }
		});
		return tree;
	}
}
