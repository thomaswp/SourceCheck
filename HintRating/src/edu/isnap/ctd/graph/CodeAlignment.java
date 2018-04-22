package edu.isnap.ctd.graph;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Consumer;

import edu.isnap.ctd.util.map.BiMap;

public class CodeAlignment {

	private final int matchReward;
	private final int renamePenalty;

	public static void main(String[] args) {
		CodeAlignment align = new CodeAlignment();

		ASTNode n1 = new ASTBuilder("A")
				.l("B", "C")
				.c("C", b -> b.p("D").p("E"))
				.get();
		System.out.println("n1: " + n1);

		ASTNode n2 = new ASTBuilder("A")
				.l("B")
				.c("C", b -> b.p("D").p("E").p("F"))
				.l("C")
				.get();
		System.out.println("n2: " + n2);

		NodePairs mapping = align.align(n1, n2);
		System.out.println(mapping.reward);
	}

	public CodeAlignment() {
		this(1, 1);
	}

	public CodeAlignment(int matchReward, int renamePenalty) {
		this.matchReward = matchReward;
		this.renamePenalty = renamePenalty;
	}

	private static class ASTBuilder {
		private final ASTNode root;

		public ASTBuilder(String rootType) {
			this(new ASTNode(rootType, null, null));
		}

		public ASTBuilder(ASTNode root) {
			this.root = root;
		}

		public ASTBuilder p(String childType) {
			ASTNode child = new ASTNode(childType, null, null);
			root.addChild(child);
			return new ASTBuilder(child);
		}

		public ASTBuilder c(String childType, Consumer<ASTBuilder> childBuilderConsumer) {
			childBuilderConsumer.accept(p(childType));
			return this;
		}

		public ASTBuilder l(String... childTypes) {
			for (String childType : childTypes) {
				ASTNode child = new ASTNode(childType, null, null);
				root.addChild(child);
			}
			return this;
		}

		public ASTNode get() {
			return root;
		}
	}

	public static class NodePairs extends BiMap<ASTNode, ASTNode> {
		private int reward;

		public int getReward() {
			return reward;
		}

		NodePairs() {
			super(IdentityHashMap::new);
		}
	}

	public NodePairs align(ASTNode a, ASTNode b) {
		NodePairs mapping = new NodePairs();
//		long time = System.currentTimeMillis();
		mapping.reward = align(a, b, mapping);
//		System.out.println("Nodes: " + a.treeSize() +
//				" Time: " + (System.currentTimeMillis() - time));
		return mapping;
	}

	private int align(ASTNode a, ASTNode b, BiMap<ASTNode, ASTNode> mapping) {
		if (a == null) return b == null ? matchReward : 0;

		int reward = matchReward;
		if (!a.shallowEquals(b, false)) reward -= renamePenalty;

		if (mapping != null) mapping.put(a, b);

		List<ASTNode> childrenA = a.children();
		List<ASTNode> childrenB = b.children();

		int nChildrenA = childrenA.size();
		int nChildrenB = childrenB.size();

		if (nChildrenA == 0 || nChildrenB == 0) {
//			System.out.println("Testing leaves: " + a + " / " + b);
			return reward;
		}

		int[][] matrix = new int[nChildrenA + 1][nChildrenB + 1];

		for (int indexA = 1; indexA <= nChildrenA; indexA++) {
			ASTNode childA = childrenA.get(indexA - 1);
			for (int indexB = 1; indexB <= nChildrenB; indexB++) {
				ASTNode childB = childrenB.get(indexB - 1);

				int pairReward = align(childA, childB, null);
				int best = Math.max(Math.max(
						matrix[indexA - 1][indexB],
						matrix[indexA][indexB - 1]),
						matrix[indexA - 1][indexB - 1] + pairReward);
				matrix[indexA][indexB] = best;
			}
		}

//		System.out.println("Matching: " + a + " and " + b);
//		for (int[] row : matrix) System.out.println(Arrays.toString(row));

		int indexA = nChildrenA, indexB = nChildrenB;
		while (indexA > 0 && indexB > 0) {
			int r = matrix[indexA][indexB];
			if (r == matrix[indexA - 1][indexB]) {
				indexA--;
				continue;
			}
			if (r == matrix[indexA][indexB - 1]) {
				indexB--;
				continue;
			}
			if (mapping != null) {
				ASTNode childA = childrenA.get(indexA - 1);
				ASTNode childB = childrenB.get(indexB - 1);
				align(childA, childB, mapping);
//				System.out.printf("\tPaired %s[%d] with %s[%d] for %d\n",
//						childrenA.get(indexA - 1).type, indexA - 1,
//						childrenB.get(indexB - 1).type, indexB - 1,
//						r - matrix[indexA - 1][indexB - 1]);
			}
			indexA--; indexB--;
		}
//		System.out.println();

		return matrix[nChildrenA][nChildrenB] + reward;
	}
}
