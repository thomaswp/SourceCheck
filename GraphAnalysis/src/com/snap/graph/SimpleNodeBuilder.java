package com.snap.graph;

import java.util.List;

import com.snap.data.Canonicalization;
import com.snap.data.Code;
import com.snap.data.Code.Accumulator;
import com.snap.graph.data.Node;

public class SimpleNodeBuilder {
	
//	public static Code fromTree(Node node) {
//		List<Code> children = new ArrayList<Code>();
//		for (Node child : node.children) {
//			children.add(fromTree(child));
//		}
//		
//		Code code;
//		switch (node.type) {
//		case "snapshot": 
//			Snapshot s = new Snapshot("", (Stage) children.get(0));
//			for (int i = 1; i < children.size(); i++) {
//				s.
//				if (s instanceof Variable)
//			}
//			code = s;
//			break;
//		case "stage":
//			code = new Stage();
//		}
//	}
	
	public static Node toTree(Code code, final boolean canon) {
		return toTree(code, canon, null);
	}
	
	private static Node toTree(Code code, final boolean canon, Node parent) {
		final Node node = new Node(parent, code.addChildren(canon, Code.NOOP));
		node.tag = code;
		code.addChildren(canon, new Accumulator() {
			@Override
			public void add(String code) {
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
			public void add(List<String> codes) {
				for (String code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Canonicalization canon) {
				node.canonicalizations.add(canon);
			}
		});
		
		return node;
	}
}
