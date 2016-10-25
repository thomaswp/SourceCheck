package edu.isnap.ctd.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

import distance.RTED_InfoTree_Opt;
import util.LblTree;

public class TreeUtils {
	
	private static LblTree genTree(String alphabet, int id, double childChance, double falloff, Random rand) {
		String label = "" + alphabet.charAt(rand.nextInt(alphabet.length()));
		LblTree tree = new LblTree(label, id);
		while (rand.nextDouble() < childChance) {
			LblTree child = genTree(alphabet, id, childChance * falloff, falloff, rand);
			tree.add(child);
		}
		
		return tree;
	}
	
	public static void main(String[] args) {
		treeTest();
	}
	
	private static void treeTest() {
//		LblTree tree1 = new LblTree("A", 0);
//		LblTree leafC = new LblTree("C", 0);
//		leafC.add(new LblTree("B", 0));
//		leafC.add(new LblTree("D", 0));
//		tree1.add(leafC);
//		tree1.add(new LblTree("F", 0));
////		tree1.add(new LblTree("E", 1));
////		tree1.add(new LblTree("C", 0));
//		
//		LblTree tree2 = new LblTree("A", 1);
//		tree2.add(new LblTree("B", 1));
//		tree2.add(new LblTree("D", 1));
//		tree2.add(new LblTree("F", 1));
////		LblTree leafC = new LblTree("C", 1);
////		leafC.add(new LblTree("B", 1));
////		tree2.add(leafC);
		
		long seed = (long)(Math.random() * 100000);
//		seed = 45715; 
		seed = 7550;
		System.out.println("Seed: " + seed);
		Random rand = new Random(seed);
		
//		double chance = 0.75;
		double chance = 0.5;
		LblTree tree1 = genTree("ABCDEF", 0, chance, 0.9, rand);
		LblTree tree2 = genTree("DEFGHI", 1, chance, 0.9, rand);
		
		System.out.println(tree1 + " vs " + tree2);
		
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
		opt.init(tree1, tree2);
		double dis = opt.nonNormalizedTreeDist(tree1, tree2);
		
		while (dis > 0) {
			TreeAction action = increment(tree1, tree2, opt);
			tree1 = action.result;
			System.out.println(action);
			
			opt.init(tree1, tree2);
			double newDis = opt.nonNormalizedTreeDist(tree1, tree2); 
			if (newDis >= dis) {
				dis = newDis;
				break;
			}
			dis = newDis;
		}
		if (dis > 0) {
			System.err.println(tree1 + " vs " + tree2 + ": " + dis);
			System.out.println(toString(tree1));
			System.out.println();
			System.out.println(toString(tree2));
		} else {
			System.out.println(tree1 + " vs " + tree2 + ": " + dis);
		}
	}

	public static LblTree copy(LblTree tree) {
		if (tree == null) return tree;
		LblTree copy = new LblTree(tree.getLabel(), tree.getTreeID());
		int childCount = tree.getChildCount();
		for (int i = 0; i < childCount; i++) {
			copy.add(copy((LblTree) tree.getChildAt(i)));
		}
		return copy;
	}
	
	public static TreeAction increment(LblTree tree1, LblTree tree2) {
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 1);
		return increment(tree1, tree2, opt);
	}
	
	public static abstract class TreeAction extends StringHashable {
		public final LblTree result;
		
		public TreeAction(LblTree result) {
			this.result = result;
		}
	}
	
	static class InsertAction extends TreeAction {

		private final String inserted;
		
		public InsertAction(LblTree result, String inserted, int insertIndex,
				String parent, int parentIndex) {
			super(result);
			this.inserted = inserted;
		}

		@Override
		protected String toCanonicalStringInternal() {
			return String.format("add %s", inserted);
		}
		
	}
	
	static class DeleteAction extends TreeAction {

		private final String deleted;
		
		public DeleteAction(LblTree result, String deleted, int deletedIndex) {
			super(result);
			this.deleted = deleted;
		}

		@Override
		protected String toCanonicalStringInternal() {
			return String.format("del %s", deleted);
		}
		
	}
	
	static class ReplaceAction extends TreeAction {

		private final String from, to;
		
		public ReplaceAction(LblTree result, String from, int fromIndex, String to, int toIndex) {
			super(result);
			this.from = from;
			this.to = to;
		}

		@Override
		protected String toCanonicalStringInternal() {
			return String.format("%s -> %s", from, to);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static TreeAction increment(LblTree tree1, LblTree tree2, RTED_InfoTree_Opt opt) {
		
		opt.init(tree1, tree2);
		
		ArrayList<LblTree> list1 = Collections.list(tree1.depthFirstEnumeration());
		ArrayList<LblTree> list2 = Collections.list(tree2.depthFirstEnumeration());
				
		LinkedList<int[]> editMapping = opt.computeEditMapping();
		for (int[] a : editMapping) {
			LblTree c1 = a[0] == 0 ? null : list1.get(a[0] - 1);
			LblTree c2 = a[1] == 0 ? null : list2.get(a[1] - 1);
			
			if (c1 != null && c2 != null && !c1.getLabel().equals(c2.getLabel())){
				TreeAction action = new ReplaceAction(tree1,
						c1.getLabel(), a[0] - 1, c2.getLabel(), a[1] - 1);
				c1.setLabel(c2.getLabel());
				return action;
			}
		}
		
		for (int[] a : editMapping) {
			LblTree c1 = a[0] == 0 ? null : list1.get(a[0] - 1);
			LblTree c2 = a[1] == 0 ? null : list2.get(a[1] - 1);
			
			if (c1 == null) {
				TreeNode parent = c2.getParent();
				if (parent == null) {
					LblTree newRoot = new LblTree(c2.getLabel(), tree1.getTreeID());
					newRoot.add(tree1);
					tree1 = newRoot;
					
					return new InsertAction(tree1, c2.getLabel(), 0, null, 0);
				}
				TreeAction insert = insert(editMapping, c2, list2, list1);
				if (insert != null) return insert;
			}
		}
		
		for (int[] a : editMapping) {
			LblTree c1 = a[0] == 0 ? null : list1.get(a[0] - 1);
			LblTree c2 = a[1] == 0 ? null : list2.get(a[1] - 1);
			
			if (c2 == null) {
				if (c1.getParent() == null) {
					if (c1.getChildCount() != 1) throw new RuntimeException("Oops");
					tree1 = (LblTree) c1.getChildAt(0);
				} else {
					removeFromParent(c1, true);
				}
				return new DeleteAction(tree1, c1.getLabel(), a[0] - 1);
			}
		}
		
		return null;
	}
	
	public static String toString(LblTree tree) {
		return toString(tree, 0);
	}
	
	private static String toString(LblTree tree, int tabs) {
		String tab = "";
		for (int i = 0; i < tabs; i++) tab += " ";
		String out = tab + tree.getLabel();
		for (int i = 0; i < tree.getChildCount(); i++) {
			String child = toString((LblTree) tree.getChildAt(i), tabs + 1);
			out += "\n" + child;
		}
		return out;
	}

	private static void removeFromParent(TreeNode child, boolean moveUpChildren) {
		int index = 0;
		LblTree parent = (LblTree) child.getParent();
		for (; index < parent.getChildCount(); index++) {
			if (parent.getChildAt(index) == child) break;
		}
		parent.remove(index);
		if (moveUpChildren) {
			while (child.getChildCount() > 0) {
				parent.insert((MutableTreeNode) child.getChildAt(0), index++);
			}
		}
	}

	private static int getPairedIndex(LinkedList<int[]> editMapping, TreeNode item, 
			ArrayList<LblTree> itemList, boolean itemInTree2) {
		
		int fromIndex = itemInTree2 ? 1 : 0;
		int toIndex = itemInTree2 ? 0 : 1;
		
		int index = 0;
		for (; index < itemList.size(); index++) {
			if (itemList.get(index) == item) break;
		}
		for (int[] a : editMapping) {
			if (a[fromIndex] == index + 1) {
				return a[toIndex] - 1;
			}
		}
		return -1;
	}
	
	private static TreeAction insert(LinkedList<int[]> editMapping, LblTree toInsert, 
			ArrayList<LblTree> insertList, ArrayList<LblTree> otherList) {
		TreeNode parent = toInsert.getParent();
		
		int parentPairIndex = getPairedIndex(editMapping, parent, insertList, true);
		
		if (parentPairIndex >= 0) {
			LblTree parentPair = otherList.get(parentPairIndex);
			int i0 = 0, i1 = 0;
			while (true) {
				if (i1 == parentPair.getChildCount()) {
					break;
				}
				if (parent.getChildAt(i0) == toInsert) {
					break;
				}
				if (((LblTree) parent.getChildAt(i0)).getLabel().equals(
						((LblTree) parentPair.getChildAt(i1)).getLabel())) {
					i0++;
					i1++;
				} else {
					i1++;
				}
			}
			
			LblTree added;
			parentPair.insert(added = new LblTree(toInsert.getLabel(), toInsert.getTreeID()), i0);
			
			for (int i = 0; i < parentPair.getChildCount(); i++) {
				TreeNode childPair = parentPair.getChildAt(i);
				if (childPair == added) continue;
				
				int childIndex = getPairedIndex(editMapping, childPair, otherList, false);
				if (childIndex < 0) continue;
				TreeNode child = insertList.get(childIndex);
				while (child != parent) {
					child = child.getParent();
					if (child == toInsert) {
						removeFromParent(childPair, false);
						added.add((MutableTreeNode) childPair);
						i--;
						break;
					}
				}
			}
			
			return new InsertAction(otherList.get(otherList.size() - 1),
					toInsert.getLabel(), i0, parentPair.getLabel(), parentPairIndex);
		}
		
		return null;
	}
}
