package edu.isnap.eval.agreement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintHighlighter.Deletion;
import edu.isnap.ctd.hint.HintHighlighter.EditHint;
import edu.isnap.ctd.hint.HintHighlighter.Insertion;
import edu.isnap.ctd.hint.HintHighlighter.Reorder;
import edu.isnap.ctd.util.Cast;

public class EditComparer {

	public static EditDifference compare(Node from, List<EditHint> a, List<EditHint> b) {
		final Map<Node, EditHint> mapA = getEditMap(a);
		final Map<Node, EditHint> mapB = getEditMap(b);

		final EditDifference diff = new EditDifference();

		from.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.hasType("script", "literal", "list")) return;
				EditHint editA = mapA.get(node);
				EditHint editB = mapB.get(node);
				diff.compareEdit(node, editA, editB);
			}
		});

		diff.compareInserts(from, filterInsertions(a), filterInsertions(b));

		return diff;
	}

	private static List<Insertion> filterInsertions(List<EditHint> edits) {
		List<Insertion> inserts = new ArrayList<>();
		for (EditHint edit : edits) {
			Insertion insert = Cast.cast(edit, Insertion.class);
			if (insert != null) inserts.add(insert);
		}
		return inserts;
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

	public static class EditDifference {

		public int[][] confusionMatrix = new int[4][];

		public int[] insertMatches = new int[4];

		public EditDifference() {
			for (int i = 0; i < confusionMatrix.length; i++) {
				confusionMatrix[i] = new int[confusionMatrix.length];
			}
		}

		public void compareInserts(Node node, List<Insertion> a, List<Insertion> b) {
			for (int m = 0; m < 2; m++) {
				for (int ai = 0; ai < a.size(); ai++) {
					Insertion insA = a.get(ai);
					if (insA.missingParent || "temp".equals(insA.parent.tag)) {
						a.remove(ai--);
						continue;
					}

					for (int bi = 0; bi < b.size(); bi++) {
						Insertion insB = b.get(bi);
						if (insB.missingParent || "temp".equals(insB.parent.tag)) {
							b.remove(bi--);
							continue;
						}

						if (!insA.type.equals(insB.type)) continue;
						if (insA.parent != insB.parent) continue;

						boolean indexMatches = insA.index == insB.index;
						if (indexMatches == (m == 0)) {
							insertMatches[m]++;
							a.remove(ai--);
							b.remove(bi--);
							break;
						}
					}
				}
			}
			insertMatches[2] = a.size();
			insertMatches[3] = b.size();
//			for (Insertion ins : a) {
//				System.out.println(ins);
//			}
//			System.out.println("-----");
//			for (Insertion ins : b) {
//				System.out.println(ins);
//			}

		}

		public void compareEdit(Node node, EditHint editA, EditHint editB) {
			int indexA = getActionIndex(node, editA);
			int indexB = getActionIndex(node, editB);
//			if (indexB == 1 && indexA != indexB) {
//				System.out.println(node);
//				System.out.println("    " + editA + "\n vs " + editB);
//				System.out.println();
//			}
			confusionMatrix[indexA][indexB]++;
		}

		public static int getActionIndex(Node node, EditHint edit) {
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

		public void print() {
			String[] labels = new String[] { "-", "d", "r", "m" };

			for (int i = 0; i < 4; i++) {
				if (i == 2) continue; // skip reorder
				int tp = 0, p = 0, l = 0;
				for (int j = 0; j < 4; j++) {
					p += confusionMatrix[i][j];
					l += confusionMatrix[j][i];
					if (i == j) tp += confusionMatrix[i][j];
				}
				printStats(tp, l, p, labels[i]);
			}

			int tp = insertMatches[0], p = tp + insertMatches[2], l = tp + insertMatches[3];
			printStats(tp, l, p, "i");
		}

		private static void printStats(int tp, int l, int p, String label) {
			double precision = (double)tp / l;
			double recall = (double)tp / p;
			System.out.printf("R[%s]: %.02f (%d/%d)\tP[%s]: %.02f (%d/%d)\n",
					label, recall, tp, p,
					label, precision, tp, l);
		}

		public static EditDifference sum(EditDifference a, EditDifference b) {
			if (a == null) return b;
			if (b == null) return a;

			EditDifference diff = new EditDifference();
			diff.confusionMatrix = sumMatrices(a.confusionMatrix, b.confusionMatrix);

			for (int i = 0; i < diff.insertMatches.length; i++) {
				diff.insertMatches[i] = a.insertMatches[i] + b.insertMatches[i];
			}

			return diff;
		}

		private static int[][] sumMatrices(int[][] a, int[][] b) {
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
	}

}
