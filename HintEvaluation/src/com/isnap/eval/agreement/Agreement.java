package com.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;

public class Agreement {

	public static void main(String[] args) {
		try {
			Snapshot a = Snapshot.parse(new File("A.xml"));
			Snapshot b = Snapshot.parse(new File("B.xml"));
			findEdits(a, b);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	private static void findEdits(Snapshot fromSnapshot, Snapshot toSnapshot) {
		Node from = SimpleNodeBuilder.toTree(fromSnapshot, true);
		Node to = SimpleNodeBuilder.toTree(toSnapshot, true);

		Map<String, Node> fromIDMap = getIDMap(from);
		Map<String, Node> toIDMap = getIDMap(to);


		System.out.println(from.prettyPrint(reverse(fromIDMap)));
		System.out.println(to.prettyPrint(reverse(toIDMap)));

	}

	private static Map<Node, String> reverse(Map<String, Node> map) {
		Map<Node, String> reverse = new IdentityHashMap<>();
		for (String id : map.keySet()) {
			reverse.put(map.get(id), id);
		}
		return reverse;
	}

	private static Map<String, Node> getIDMap(Node node) {
		Map<String, Node> idMap = new HashMap<>();
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.hasType("script")) {
					String id = "script(" + node.parent.id + ":" + node.index() + ")";
					idMap.put(id, node);
				} else {
					idMap.put(node.id, node);
				}
			}
		});
		return idMap;
	}

}
