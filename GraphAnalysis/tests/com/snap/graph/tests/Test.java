package com.snap.graph.tests;

import static org.junit.Assert.*;

import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.parser.Assignment;
import com.snap.parser.Store.Mode;

public class Test {

	@org.junit.Test
	public void test() {
		SnapSubtree subtree = new SnapSubtree(Assignment.Fall2015.GuessingGame1);
		SubtreeBuilder builder = subtree.buildGraph(Mode.Use, 1);
//		Node node = new 
//		builder.getHints(parent)
	}

}
