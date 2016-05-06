package com.snap.graph.tests;

import static org.junit.Assert.assertArrayEquals;

import java.io.FileNotFoundException;
import java.util.List;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.parser.Assignment;
import com.snap.parser.Store.Mode;

public class Test {

	Assignment[] assignments = new Assignment[] {
		Assignment.Fall2015.GuessingGame1	
	};
	
	@org.junit.Test
	public void test() throws FileNotFoundException {
		for (Assignment assignment : assignments) {
			SnapSubtree subtree = new SnapSubtree(Assignment.Fall2015.GuessingGame1);
			SubtreeBuilder builder = subtree.buildGraph(Mode.Use, 1);
			
			Snapshot test = assignment.loadTest("join");
			Node node = SimpleNodeBuilder.toTree(test, true);
			List<Node> says = node.searchAll(new Node.TypePredicate("doSayFor"));
			Node say = says.get(1);
			VectorHint hint = (VectorHint) builder.getFirstHint(say);
			assertArrayEquals(hint.to.items, new String[] {
				"reportJoinWords", "literal"	
			});
			
		}
		
//		Node node = new 
//		builder.getHints(parent)
	}

}
