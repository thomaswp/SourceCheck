package com.snap.graph.tests;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SnapSubtree;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.parser.Assignment;
import com.snap.parser.DataRow;
import com.snap.parser.SolutionPath;
import com.snap.parser.Store.Mode;

public class Testing {

	Assignment[] assignments = new Assignment[] {
		Assignment.Fall2015.GuessingGame1	
	};
	
	private static Node toNode(Snapshot snapshot) {
		return SimpleNodeBuilder.toTree(snapshot, true);
	}
	
	private HashMap<Assignment, SubtreeBuilder> builders = new HashMap<>();

	private SubtreeBuilder getBuilder(Assignment assignment) {
		SubtreeBuilder builder = builders.get(assignment);
		if (builder == null) {
			SnapSubtree subtree = new SnapSubtree(assignment);
			builder = subtree.buildGraph(Mode.Use, 1);
			builders.put(assignment, builder);
		}
		return builder;
	}
	
	private VectorHint getFirstHint(Assignment assignment, Node node) {
		SubtreeBuilder builder = getBuilder(assignment);
		return (VectorHint) builder.getFirstHint(node);
	}
	
	@Test
	public void test() throws FileNotFoundException {
		for (Assignment assignment : assignments) {
			Snapshot test = assignment.loadTest("join");
			Node node = toNode(test);
			List<Node> says = node.searchAll(new Node.TypePredicate("doSayFor"));
			Node say = says.get(1);
			VectorHint hint = getFirstHint(assignment, say);
			assertArrayEquals(hint.to.items, new String[] {
				"reportJoinWords", "literal"	
			});
			
			say = says.get(0);
			hint = getFirstHint(assignment, say);
			assertNull(hint);
			
		}
		
//		Node node = new 
//		builder.getHints(parent)
	}
	
	@Test
	public void test2() {
		Snapshot snapshot = getSnapshot(Assignment.Spring2016.GuessingGame1, "f5de3d7e-61e9-4a64-88d4-bf5adcee3e6f", 361577);
		assertNotNull(snapshot);
		
		Node node = toNode(snapshot);
		Node script = node.search(new Node.TypePredicate("script"));
		
		VectorHint hint = getFirstHint(Assignment.Fall2015.GuessingGame1, script);
		assertArrayEquals(hint.to.items, new String[] {
			"receiveGo", "doSayFor", "doAsk", "doSayFor"	
		});
	}
	
	private Snapshot getSnapshot(Assignment assignment, String id, int rowID) {
		SolutionPath submission = assignment.loadSubmission(id, Mode.Use, true);
		Snapshot snapshot = null;
		for (DataRow row : submission) {
			if (row.snapshot != null) snapshot = row.snapshot;
			if (row.id == rowID) {
				return snapshot;
			}
		}
		return null;
	}

}
