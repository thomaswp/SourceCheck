package com.snap.graph;

import java.util.List;

import com.snap.data.Canonicalization;
import com.snap.data.Code;
import com.snap.data.Code.Accumulator;

import util.LblTree;

public class SimpleTreeBuilder {

	public static LblTree toTree(Code code, final int id, final boolean canon) {
		final LblTree tree = new LblTree(null, id);
		tree.setLabel(code.addChildren(canon, new Accumulator() {
			@Override
			public void add(String code) {
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
			public void add(List<String> codes) {
				for (String code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Canonicalization canon) { }
		}));
		return tree;
	}
}
