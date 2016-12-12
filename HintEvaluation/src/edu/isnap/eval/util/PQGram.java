package edu.isnap.eval.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.eval.util.PQGram.Profile.Constructor;

public class PQGram {

	public static void main(String[] args) {
		Node s = new Node(null, "A");
		s.addChild("B");
		s.addChild("C").addChild("Q");
		
		
		Node t = new Node(null, "Z");
		t.addChild("Q");
		t.addChild("C").addChild("Y");
		
		testEdits(s, t);
		
//		Profile ps = new Profile(s);
//		System.out.println(s);
//		System.out.println(ps);
//
//		System.out.println();
//		
//		Profile pt = new Profile(t);
//		System.out.println(t);
//		System.out.println(pt);
//
//		Set<Insertion> insertions = new HashSet<>();
//		Set<Deletion> deletions = new HashSet<>();
//		Set<Relabel> relabels = new HashSet<>();
//		BiMap<Node, Node> mapping = new BiMap<>();
//		ps.addEdits(pt, insertions, deletions, relabels, mapping);
//		System.out.println(ps.editDistance(pt));
//		System.out.println(insertions);
//		System.out.println(deletions);
//		System.out.println(relabels);
//		System.out.println(mapping);
	}
	
	private static void testEdits(Node from, Node to) {
		
		List<Node> edits = edits(from, to);
		if (edits.size() == 0) {
			if (!from.equals(to)) { 
				System.err.println(from + " =!= " + to);
			}
			return;
		}

		System.out.println(from + " --> " + to);
		for (Node edit : edits) {
			System.out.println(" + " + edit);
		}
		
		for (Node edit : edits) {
			testEdits(edit, to);
		}
	}
	
	public static List<Node> edits(Node from, Node to) {
		Profile pFrom = new Profile(from), pTo = new Profile(to);

		Set<Insertion> insertions = new HashSet<>();
		Set<Deletion> deletions = new HashSet<>();
		Set<Relabel> relabels = new HashSet<>();
		BiMap<Node, Node> mapping = new BiMap<>();
		pFrom.addEdits(pTo, insertions, deletions, relabels, mapping);
		
		List<Node> outcomes = new LinkedList<>();
		for (NodeEdit edit : insertions) {
			Node outcome = edit.outcome();
			if (outcome != null) outcomes.add(outcome);
		}
		for (NodeEdit edit : deletions) {
			Node outcome = edit.outcome();
			if (outcome != null) outcomes.add(outcome);
		}
		for (NodeEdit edit : relabels) {
			Node outcome = edit.outcome();
			if (outcome != null) outcomes.add(outcome);
		}
		
		return outcomes;
	}
	
	public interface NodeEdit {
		Node outcome();
	}
	
	public static class Insertion extends Tuple<Node, Node> implements NodeEdit {

		private BiMap<Node, Node> mapping;
		
		public final static Constructor<Insertion> constructor = new Constructor<Insertion>() {
			@Override
			public Insertion construct(Node x, Node y, BiMap<Node, Node> mapping) {
				return new Insertion(x, y, mapping);
			}
			
		};
		
		public Insertion(Node x, Node y, BiMap<Node, Node> mapping) {
			super(x, y);
			this.mapping = mapping;
		}

		@Override
		public Node outcome() {
			Node toParent = x;
			Node fromParent = mapping.getTo(toParent);
			if (fromParent == null) return null;
			fromParent = fromParent.copy(false);
			
			// Walk through the both parents' children
			int toIndex = 0, fromIndex = 0;
			while (true) {
				if (fromIndex == fromParent.children.size()) {
					// If we reach the end of from's children, we must break
					break;
				}
				if (toParent.children.get(toIndex) == y) {
					// If we reach the node we want to insert, we break
					break;
				}
				if (toParent.children.get(toIndex).hasType(
						fromParent.children.get(fromIndex).type())) {
					// Otherwise, if the two nodes have the same label, increment both
					fromIndex++;
					toIndex++;
				} else {
					// Otherwise, skip one of the (presumably to be deleted) from children
					fromIndex++;
				}
			}
			
			
			Node insert = new Node(fromParent, y.type());
			fromParent.children.add(fromIndex, insert);
			return fromParent.root();
		}
		
	}
	
	public static class Deletion extends Tuple<Node, Node> implements NodeEdit {

		public final static Constructor<Deletion> constructor = new Constructor<Deletion>() {
			@Override
			public Deletion construct(Node x, Node y, BiMap<Node, Node> mapping) {
				return new Deletion(x, y);
			}
			
		};
		
		public Deletion(Node x, Node y) {
			super(x, y);
		}

		@Override
		public Node outcome() {
			Node copy = y.copy(false);
			copy.parent.children.remove(copy.index());
			return copy.root();
		}
		
	}
	
	public static class Relabel extends Tuple<Node, Node> implements NodeEdit {

		public Relabel(Node x, Node y) {
			super(x, y);
		}

		@Override
		public Node outcome() {
			Node copy = x.copy(false);
			copy.setType(y.type());
			return copy.root();
		}
		
	}
	
	static class Profile {
		public final int p, q;
		
		private final List<ShiftRegister> list = new LinkedList<ShiftRegister>();
		
//	    def __init__(self, root, p=2, q=3):
//      """
//          Builds the PQ-Gram Profile of the given tree, using the p and q parameters specified.
//          The p and q parameters do not need to be specified, however, different values will have
//          an effect on the distribution of the calculated edit distance. In general, smaller values
//          of p and q are better, though a value of (1, 1) is not recommended, and anything lower is
//          invalid.
//      """
//      super(Profile, self).__init__()
//      ancestors = ShiftRegister(p)
//      self.list = list()
//      
//      self.profile(root, p, q, ancestors)
//      self.sort()
		public Profile(Node root) {
			this(root, 2, 3);
		}
		
		public Profile(Node root, int p, int q) {
			this.p = p;
			this.q = q;
			
			ShiftRegister ancestors = new ShiftRegister(p);
			
			profile(root, ancestors);
			sort();
		}
		
//	    def profile(self, root, p, q, ancestors):
//      """
//          Recursively builds the PQ-Gram profile of the given subtree. This method should not be called
//          directly and is called from __init__.
//      """
//      ancestors.shift(root.label)
//      siblings = ShiftRegister(q)
//      
//      if(len(root.children) == 0):
//          self.append(ancestors.concatenate(siblings))
//      else:
//          for child in root.children:
//              siblings.shift(child.label)
//              self.append(ancestors.concatenate(siblings))
//              self.profile(child, p, q, copy.deepcopy(ancestors))
//          for i in range(q-1):
//              siblings.shift("*")
//              self.append(ancestors.concatenate(siblings))
		private void profile(Node root, ShiftRegister ancestors) {
			ancestors.shift(root);
			ShiftRegister siblings = new ShiftRegister(q);
			
			if (root.children.isEmpty()) {
				list.add(ancestors.concatenate(siblings));
			} else {
				for (Node child : root.children) {
					siblings.shift(child);
					list.add(ancestors.concatenate(siblings));
					profile(child, ancestors.copy());
				}
				for (int i = 0; i < q - 1; i++) {
					siblings.shift(null);
					list.add(ancestors.concatenate(siblings));
				}
			}
		}
		
//	    def edit_distance(self, other):
//      """
//          Computes the edit distance between two PQ-Gram Profiles. This value should always
//          be between 0.0 and 1.0. This calculation is reliant on the intersection method.
//      """
//      union = len(self) + len(other)
//      return 1.0 - 2.0*(self.intersection(other)/union)
		
		public double editDistance(Profile other) {
			int union = list.size() + other.list.size();
			return 1.0 - 2.0 * intersection(other) / union;
		}
		
//	    def intersection(self, other):
//      """
//          Computes the set intersection of two PQ-Gram Profiles and returns the number of
//          elements in the intersection.
//      """
//      intersect = 0.0
//      i = j = 0
//      while i < len(self) and j < len(other):
//          intersect += self.gram_edit_distance(self[i], other[j])
//          if self[i] == other[j]:
//              i += 1
//              j += 1
//          elif self[i] < other[j]:
//              i += 1
//          else:
//              j += 1
//      return intersect
		
		public void addEdits(Profile other, Set<Insertion> insertions, 
				Set<Deletion> deletions, Set<Relabel> relabels, 
				BiMap<Node,Node> mapping) {
			
			List<ShiftRegister> missing = new LinkedList<>(), extra = new LinkedList<>();

			Set<Node> commonRoots = new HashSet<>();
			commonRoots.add(null);
			
			// Construct list of extra and missing registers
			int i1 = 0, i2 = 0;
			while (i1 < list.size() && i2 < other.list.size()) {
				if (list.get(i1).equals(other.list.get(i2))) {
					for (int i = 0; i < p + q; i++) {
						Node n1 = list.get(i1).register.get(i);
						Node n2 = other.list.get(i2).register.get(i);
						if (n1 != null && n2 != null) {
							if (i >= p - 1) {
								mapping.put(n1, n2);
							}
							commonRoots.add(n1);
							commonRoots.add(n2);
						}
					}
					i1++;
					i2++;
				} else if (list.get(i1).compareTo(other.list.get(i2)) < 0) {
					extra.add(list.get(i1));
					i1++;
				} else {
					missing.add(other.list.get(i2));
					i2++;
				}
			}
			while (i1 < list.size()) {
				extra.add(list.get(i1));
				i1++;
			}
			while (i2 < other.list.size()) {
				missing.add(other.list.get(i2));
				i2++;
			}
			
			for (ShiftRegister reg : missing) {
				extractEdits(insertions, commonRoots, reg, mapping, Insertion.constructor);
			}
			
			for (ShiftRegister reg : extra) {
				extractEdits(deletions, commonRoots, reg, mapping, Deletion.constructor);
			}
			
			HashSet<Tuple<Node, Node>> toRemove = new HashSet<>();
			for (Tuple<Node, Node> ins : insertions) {
				for (Tuple<Node, Node> del : deletions) {
					if (ShiftRegister.nodesEqual(ins.x, del.x)) {
						relabels.add(new Relabel(del.y, ins.y));
						mapping.put(del.y, ins.y);
						toRemove.add(ins);
						toRemove.add(del);
					}
				}
			}
			for (Tuple<Node, Node> r : toRemove) {
				insertions.remove(r);
				deletions.remove(r);
			}
			
			
//			toRemove.clear();
//			for (Tuple<Node, Node> ins : insertions) {
//				for (Tuple<Node, Node> del : deletions) {
//					if (ShiftRegister.nodesEqual(mapping.getFrom(del.x), ins.x)) {
//						relabels.add(new Relabel(del.y, ins.y));
//						mapping.put(del.y, ins.y);
//						toRemove.add(ins);
//						toRemove.add(del);
//					}
//				}
//			}
//			for (Tuple<Node, Node> r : toRemove) {
//				insertions.remove(r);
//				deletions.remove(r);
//			}
			
			toRemove.clear();
			for (Tuple<Node, Node> ins : insertions) {
				for (Tuple<Node, Node> del : deletions) {
					if (ShiftRegister.nodesEqual(ins.y, del.y) && 
							ShiftRegister.nodesEqual(ins.x, mapping.getFrom(del.x))) {
						toRemove.add(ins);
						toRemove.add(del);
					}
				}
			}
			for (Tuple<Node, Node> r : toRemove) {
				insertions.remove(r);
				deletions.remove(r);
			}
		}
		
		interface Constructor<T> {
			T construct(Node x, Node y, BiMap<Node, Node> mapping);
		}

		private <T extends Tuple<Node, Node>> void extractEdits(Set<T> registers, 
				Set<Node> commonRoots, ShiftRegister reg, BiMap<Node, Node> mapping, 
				Constructor<T> constructor) {
			for (int i = 1; i < p; i++) {
				Node ai = reg.register.get(i);
				if (!commonRoots.contains(ai)) {
					registers.add(constructor.construct(reg.register.get(i - 1), ai, mapping));
				}
			}
			for (int i = 0; i < q; i++) {
				Node ci = reg.register.get(p + i);
				if (!commonRoots.contains(ci)) {
					registers.add(constructor.construct(reg.register.get(p - 1), ci, mapping));
				}
			}
		}
		
		public double intersection(Profile other) {
			double intersect = 0;
			int i = 0, j = 0;
			while (i < list.size() && j < other.list.size()) {
				intersect += gramEditDistance(list.get(i), other.list.get(j));
				if (list.get(i).equals(other.list.get(j))) {
					i++;
					j++;
				} else if (list.get(i).compareTo(other.list.get(j)) < 0) {
					i++;
				} else {
					j++;
				}
			}
			return intersect;
		}
		
//	    def gram_edit_distance(self, gram1, gram2):
//      """
//          Computes the edit distance between two different PQ-Grams. If the two PQ-Grams are the same
//          then the distance is 1.0, otherwise the distance is 0.0. Changing this will break the
//          metrics of the algorithm.
//      """
//      distance = 0.0
//      if gram1 == gram2:
//          distance = 1.0
//      return distance
		
		private double gramEditDistance(ShiftRegister gram1, ShiftRegister gram2) {
			return gram1.equals(gram2) ? 1 : 0;
		}
		
//	    def sort(self):
//      """
//          Sorts the PQ-Grams by the concatenation of their labels. This step is automatically performed
//          when a PQ-Gram Profile is created to ensure the intersection algorithm functions properly and
//          efficiently.
//      """
//      self.list.sort(key=lambda x: ''.join)
		
		private void sort() {
			Collections.sort(list);
		}
		
		@Override
		public String toString() {
			String s = "";
			for (ShiftRegister r : list) {
				s += r + "\n";
			}
			return s;
		}
	}

//    Represents a register which acts as a fixed size queue. There are only two valid
//    operations on a ShiftRegister: shift and concatenate. Shifting results in a new
//    value being pushed onto the end of the list and the value at the beginning list being
//    removed. Note that you cannot recover this value, nor do you need to for generating
//    PQ-Gram Profiles.
	private static class ShiftRegister implements Comparable<ShiftRegister> {
		
		private final List<Node> register = new LinkedList<Node>();
		
		private transient String str;
		
//	    def __init__(self, size):
//        """
//            Creates an internal list of the specified size and fills it with the default value
//            of "*". Once a ShiftRegister is created you cannot change the size without 
//            concatenating another ShiftRegister.
//        """
//        self.register = list()
//        for i in range(size):
//            self.register.append("*")
		public ShiftRegister(int size) {
			for (int i = 0; i < size; i++) {
				register.add(null);
			}
			updateStr();
		}
		
		private void add(ShiftRegister reg) {
			this.register.addAll(reg.register);
			updateStr();
		}
		
		private void updateStr() {
			String s = "[";
			for (int i = 0; i < register.size(); i++) {
				if (i > 0) s += ", ";
				s += nodeToString(register.get(i));
			}
			str = s + "]";
		}
		
		public static String nodeToString(Node node) {
			return node == null ? "*" : node.type();
		}
		
		public static boolean nodesEqual(Node a, Node b) {
			if (a == null) return b == null;
			return a.shallowEquals(b);
		}
		 
//    def concatenate(self, reg):
//        """
//            Concatenates two ShiftRegisters and returns the resulting ShiftRegister.
//        """
//        temp = list(self.register)
//        temp.extend(reg.register)
//        return temp
		
		public ShiftRegister concatenate(ShiftRegister reg) {
			ShiftRegister temp = new ShiftRegister(0);
			temp.add(this);
			temp.add(reg);
			return temp;
		}
		
//	    def shift(self, el):
//        """
//            Shift is the primary operation on a ShiftRegister. The new item given is pushed onto
//            the end of the ShiftRegister, the first value is removed, and all items in between shift 
//            to accomodate the new value.
//        """
//        self.register.pop(0)
//        self.register.append(el)
		public void shift(Node el) {
			register.remove(0);
			register.add(el);
			updateStr();
		}
		
		public ShiftRegister copy() {
			ShiftRegister reg = new ShiftRegister(0);
			reg.add(this);
			return reg;
		}
		
		public boolean equals(ShiftRegister other) {
			return str.equals(other.str);
		}
		
		@Override
		public String toString() {
			return str;
		}

		@Override
		public int compareTo(ShiftRegister o) {
			return str.compareTo(o.str);
		}
	}
//
//def build_extended_tree(root, p=1, q=1):
//    """
//        This method will take a normal tree structure and the given values for p and q, returning
//        a new tree which represents the so-called PQ-Extended-Tree.
//        
//        To do this, the following algorithm is used:
//            1) Add p-1 null ancestors to the root
//            2) Traverse tree, add q-1 null children before the first and
//               after the last child of every non-leaf node
//            3) For each leaf node add q null children
//    """
//    original_root = root # store for later
//    
//    # Step 1
//    for i in range(p-1):
//        node = tree.Node(label="*")
//        node.addkid(root)
//        root = node
//        
//    # Steps 2 and 3
//    list_of_children = original_root.children
//    if(len(list_of_children) == 0):
//        q_append_leaf(original_root, q)
//    else:
//        q_append_non_leaf(original_root, q)
//    while(len(list_of_children) > 0):
//        temp_list = list()
//        for child in list_of_children:
//            if(child.label != "*"):
//                if(len(child.children) == 0):
//                    q_append_leaf(child, q)
//                else:
//                    q_append_non_leaf(child, q)
//                    temp_list.extend(child.children)
//        list_of_children = temp_list
//    return root
//
//##### Extended Tree Functions #####
//    
//def q_append_non_leaf(node, q):
//    """
//        This method will append null node children to the given node. (Step 2)
//    
//        When adding null nodes to a non-leaf node, the null nodes should exist on both side of
//        the real children. This is why the first of each pair of children added sets the flag
//        'before=True', ensuring that on the left and right (or start and end) of the list of 
//        children a node is added.
//    """
//    for i in range(q-1):
//        node.addkid(tree.Node("*"), before=True)
//        node.addkid(tree.Node("*"))
//
//def q_append_leaf(node, q):
//    """
//        This method will append q null node children to the given node. (Step 3)
//    """
//    for i in range(q):  node.addkid(tree.Node("*"))
	
}
