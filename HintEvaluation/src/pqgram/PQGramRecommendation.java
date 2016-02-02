package pqgram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pqgram.edits.Deletion;
import pqgram.edits.Edit;
import pqgram.edits.Insertion;
import pqgram.edits.PositionalEdit;
import pqgram.edits.Relabeling;
import astrecognition.Settings;
import astrecognition.model.Graph;
import astrecognition.model.Tree;
/**
 * Attempts to find minimal number of steps to transform a source tree to the given target via insertions, deletions, and relabelings
 */
public class PQGramRecommendation {

	public static List<Edit> getEdits(Profile profile1, Profile profile2, Tree sourceTree, Tree targetTree) {
		Profile common = profile1.intersect(profile2);
		Profile missing = profile2.difference(common);
		Profile extra = profile1.difference(common);

		Map<String, Tree> built = new HashMap<String, Tree>();
		Map<String, String> childToParent = new HashMap<String, String>();

		buildCommonTrees(common, built, childToParent);
		
		List<Deletion> deletions = getDeletions(extra, built, childToParent);
		System.out.println("Number of deletions = " + deletions.size());
		List<Insertion> insertions = getInsertions(missing, built, childToParent);
		System.out.println("Number of insertions = " + insertions.size());
		
		// minimizing deletions/insertions by finding eligible relabelings and matching up pairs due to relabeling propagations
		List<Relabeling> relabelingEdits = new ArrayList<Relabeling>();
		Map<String, String> relabelings = RecommendationMinimizer.getRelabelings(insertions, deletions, relabelingEdits, sourceTree, targetTree);
		RecommendationMinimizer.minimizeDeletions(insertions, deletions, relabelings);
		RecommendationMinimizer.minimizeInsertions(insertions, deletions, relabelings);
		
		System.out.println("Number of deletions = " + deletions.size());
		System.out.println("Number of insertions = " + insertions.size());
				
		List<Edit> edits = new ArrayList<Edit>();
		edits.addAll(relabelingEdits);
		
//		for (String oldName : relabelings.keySet()) {
//			if (!oldName.equals(relabelings.get(oldName)) && !sourceTree.find(oldName).getOriginalLabel().equals(targetTree.find(relabelings.get(oldName)).getOriginalLabel())) {
//				edits.add(new Relabeling(oldName, relabelings.get(oldName)));
//			}
//		}
		
		for (Deletion deletion : deletions) {
			Tree correspondingTree = sourceTree.find(deletion.getB());
			if (correspondingTree != null) {
				deletion.setLineNumber(correspondingTree.getLineNumber());
				deletion.setStartPosition(correspondingTree.getStartPosition());
				deletion.setEndPosition(correspondingTree.getEndPosition());
			}
		}
		for (int i = 0; i < insertions.size(); i++) {
			Insertion insertion = insertions.get(i);
			Tree correspondingTree = sourceTree.find(insertion.getA());
			//Tree nextChild = correspondingTree.getChildren().get(insertion.getPosition());
			if (correspondingTree != null) {
				insertion.setLineNumber(correspondingTree.getLineNumber() - 1);
				insertion.setStartPosition(correspondingTree.getStartPosition());
				insertion.setEndPosition(correspondingTree.getEndPosition());
			} else {
				if (i > 0) {
					insertion.setLineNumber(insertions.get(i - 1).getLineNumber());
					insertion.setStartPosition(insertions.get(i - 1).getStartPosition());
					insertion.setEndPosition(insertions.get(i - 1).getEndPosition());
				}
			}
			System.out.println(insertion + ", " + insertion.getLineNumber());
		}
		
		edits.addAll(deletions);
		edits.addAll(insertions);
		
		// Condensing edits that correspond to the same place in the code
		Collections.sort(edits, new Comparator<Edit>() {
			public int compare(Edit edit1, Edit edit2) {
				return edit1.getLineNumber() - edit2.getLineNumber();
			}
		});

		return edits;
	}
	
	private static List<Deletion> getDeletions(Profile extra, Map<String, Tree> built, Map<String, String> childToParent) {
		List<PositionalEdit> posEdits = getPositionalEdits(extra, built, childToParent);
		
		List<Deletion> deletions = new ArrayList<Deletion>();
		for (PositionalEdit posEdit : posEdits) {
			Deletion deletion = new Deletion(posEdit.getA(), posEdit.getB(), posEdit.getAG(), posEdit.getBG(), posEdit.getPosition());
			deletion.setLineNumber(posEdit.getLineNumber());
			deletion.setStartPosition(posEdit.getStartPosition());
			deletion.setEndPosition(posEdit.getEndPosition());
			deletions.add(deletion);
		}
		return deletions;
	}
	
	private static List<Insertion> getInsertions(Profile missing, Map<String, Tree> built, Map<String, String> childToParent) {
		List<PositionalEdit> posEdits = getPositionalEdits(missing, built, childToParent);
		
		List<Insertion> insertions = new ArrayList<Insertion>();
		for (PositionalEdit posEdit : posEdits) {
			Insertion insertion = new Insertion(posEdit.getA(), posEdit.getB(), posEdit.getAG(), posEdit.getBG(), posEdit.getPosition(), posEdit.getPosition());
			insertion.setLineNumber(posEdit.getLineNumber());
			insertion.setStartPosition(posEdit.getStartPosition());
			insertion.setEndPosition(posEdit.getEndPosition());
			insertions.add(insertion);
		}
		return insertions;
	}

	private static List<PositionalEdit> getPositionalEdits(Profile pieces, Map<String, Tree> built, Map<String, String> childToParent) {
		pieces = pieces.clone();
		built = Utilities.cloneMap(built);
		childToParent = Utilities.cloneMap(childToParent);
		
		List<PositionalEdit> edits = new ArrayList<PositionalEdit>();
		
		// each 2,3-Gram looks like (ancestor, parent, child1, child2, child3)
		for (Tuple<Graph> tup : pieces.getAllElements()) {
			Tree ancestor = getTree(tup.get(0), built);
			Tree parent = ancestor;
			int position;
			for (int i = 1; i < Settings.P; i++) {
				parent = getTree(tup.get(1), built);
				position = addChildToParent(ancestor, parent, childToParent);
				if (position >= 0) {
					PositionalEdit positionalEdit = new PositionalEdit(ancestor.getUniqueLabel(), parent.getUniqueLabel(), ancestor, parent, position);
					positionalEdit.setLineNumber(ancestor.getLineNumber());
					positionalEdit.setStartPosition(ancestor.getStartPosition());
					positionalEdit.setEndPosition(ancestor.getEndPosition());
					edits.add(positionalEdit);
				}
				ancestor = parent;
			}
			for (int i = Settings.P; i < tup.length(); i++) {				
				Graph currentGraph = tup.get(i);
				Tree currentTree = getTree(currentGraph, built);
				position = addChildToParent(parent, currentTree, childToParent);
				if (position >= 0 && !currentGraph.equals(PQGram.STAR_LABEL)) {
					PositionalEdit positionalEdit = new PositionalEdit(parent.getUniqueLabel(), currentGraph.getUniqueLabel(), parent, currentGraph, position);
					positionalEdit.setLineNumber(parent.getLineNumber());
					positionalEdit.setStartPosition(parent.getStartPosition());
					positionalEdit.setEndPosition(parent.getEndPosition());
					edits.add(positionalEdit);
				}
			}
		}
		
		Collections.reverse(edits);
		for (PositionalEdit edit : edits) {
			Tree parent = built.get(edit.getA());
			parent.deleteChild(edit.getPosition());
		}
		Collections.reverse(edits);
		return edits;
	}
	
	// if tree has already been constructed, grab it; otherwise create it and add it
	private static Tree getTree(String label, Map<String, Tree> builtTrees) {
		if (!builtTrees.containsKey(label)) {
			builtTrees.put(label, new Tree(label));
		}
		return builtTrees.get(label);
	}
	
	private static Tree getTree(Graph graph, Map<String, Tree> builtTrees) {
		Tree tree = getTree(graph.getUniqueLabel(), builtTrees);
		if (graph instanceof Tree) {
			Tree graphTree = (Tree) graph;
			tree.setLineNumber(graphTree.getLineNumber());
			tree.setStartPosition(graphTree.getStartPosition());
			tree.setEndPosition(graphTree.getEndPosition());
		}
		return tree;
	}
	
	// hook up a child to its parent if not already done and return position; return -1 if already done
	private static int addChildToParent(Tree parent, Tree child, Map<String, String> childToParent) {
		if (!childToParent.containsKey(child.getUniqueLabel())) {
			childToParent.put(child.getUniqueLabel(), parent.getUniqueLabel());
			return parent.addChild(child);
		}
		return -1;
	}

	private static Set<Tree> buildCommonTrees(Profile common, Map<String, Tree> built, Map<String, String> childToParent) {
		Set<Tree> commonTrees = new HashSet<Tree>();
		for (Tuple<Graph> tup : common.getAllElements()) {
			Tree ancestor = getTree(tup.get(0), built);
			commonTrees.add(ancestor);
			Tree parent = ancestor;
			for (int i = 1; i < Settings.P; i++) {
				parent = getTree(tup.get(1), built);
				addChildToParent(parent, ancestor, childToParent);
				ancestor = parent;
			}
			for (int i = Settings.P; i < tup.length(); i++) {				
				Graph currentGraph = tup.get(i);
				Tree currentTree = getTree(currentGraph, built);
				commonTrees.add(currentTree);
				addChildToParent(parent, currentTree, childToParent);
				commonTrees.remove(currentTree);
			}
		}
		if (commonTrees.size() == 1) {
			Tree falseRoot = commonTrees.toArray(new Tree[1])[0];
			if (falseRoot.getUniqueLabel().equals(PQGram.STAR_LABEL)) {
				commonTrees.remove(falseRoot);
				commonTrees.add(falseRoot.getChildren().get(0));
			}
		}
		return commonTrees;
	}
}