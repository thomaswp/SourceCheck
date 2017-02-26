package edu.isnap.eval.agreement;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintHighlighter.Deletion;
import edu.isnap.ctd.hint.HintHighlighter.EditHint;
import edu.isnap.ctd.hint.HintHighlighter.Insertion;
import edu.isnap.ctd.hint.HintHighlighter.Reorder;

public class EditComparer {

	public static int[][] compare(Node from, List<EditHint> a, List<EditHint> b) {
		final Map<Node, EditHint> mapA = getEditMap(a);
		final Map<Node, EditHint> mapB = getEditMap(b);

		int[][] confusionMatrix = new int[4][];
		for (int i = 0; i < confusionMatrix.length; i++) {
			confusionMatrix[i] = new int[confusionMatrix.length];
		}

		from.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.hasType("script", "literal", "list")) return;
				EditHint editA = mapA.get(node);
				EditHint editB = mapB.get(node);
				int indexA = getActionIndex(node, editA);
				int indexB = getActionIndex(node, editB);
				if (indexA != indexB) System.out.println("    " + editA + "\n vs " + editB);
				confusionMatrix[indexA][indexB]++;
			}
		});
		return confusionMatrix;
	}

	public static void printMatrix(int[][] confusionMatrix) {
		for (int[] row : confusionMatrix) {
			System.out.println(Arrays.toString(row));
		}
	}

	public static int[][] sumMatrices(int[][] a, int[][] b) {
		if (b == null) return a;
		int[][] sum = new int[a.length][];
		for (int i = 0; i < a.length; i++) {
			sum[i] = new int[a[i].length];
			for (int j = 0; j < a[i].length; j++) {
				sum[i][j] = a[i][j] + b[i][j];
			}
		}
		return sum;
	}

	private static int getActionIndex(Node node, EditHint edit) {
		if (edit instanceof Deletion) {
			return 1;
		} else if (edit instanceof Reorder) {
			return 2;
		} if (edit instanceof Insertion) {
			Node candidate = ((Insertion) edit).candidate;
			if (candidate == node) return 3;
		}
		return 0;
	}

	private static Map<Node, EditHint> getEditMap(List<EditHint> edits) {
		Map<Node, EditHint> map = new IdentityHashMap<>();
		// TODO: check for duplicates
		for (EditHint edit : edits) {
			if (edit instanceof Deletion) {
				map.put(((Deletion) edit).node, edit);
			} else if (edit instanceof Reorder) {
				map.put(((Reorder) edit).node, edit);
			} if (edit instanceof Insertion) {
				Node candidate = ((Insertion) edit).candidate;
				if (candidate != null) map.put(candidate, edit);
				// TODO: handle replacements?
			}
		}
		return map;
	}

}
