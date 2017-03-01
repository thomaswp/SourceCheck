package edu.isnap.eval.agreement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVPrinter;

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

	public static EditDifference compare(Node from, List<EditHint> a1, List<EditHint> a2,
			List<EditHint> b, boolean intersect) {
		final Map<Node, EditHint> mapA1 = getEditMap(a1);
		final Map<Node, EditHint> mapA2 = getEditMap(a2);
		final Map<Node, EditHint> mapB = getEditMap(b);

		final EditDifference diff = new EditDifference();

		from.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.hasType("script", "literal", "list")) return;
				EditHint editA1 = mapA1.get(node);
				EditHint editA2 = mapA2.get(node);
				EditHint editB = mapB.get(node);
				diff.compareEdit(node, editA1, editA2, editB, intersect);
			}
		});

		diff.compareInserts(from, filterInsertions(a1), filterInsertions(a2), filterInsertions(b),
				intersect);

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
		for (EditHint edit : edits) {
			Node node = getRef(edit);
			if (node != null) safeAdd(map, node, edit);
			Node replaceRef = getReplacementRef(edit);
			if (node != null) safeAdd(map, replaceRef, edit, true);
		}
		return map;
	}

	private static Node getRef(EditHint edit) {
		if (edit instanceof Deletion) {
			return ((Deletion) edit).node;
		} else if (edit instanceof Reorder) {
			return ((Reorder) edit).node;
		} if (edit instanceof Insertion) {
			Node candidate = ((Insertion) edit).candidate;
			if (candidate != null) return candidate;
		}
		return null;
	}

	private static Node getReplacementRef(EditHint edit) {
		if ((edit instanceof Insertion)) {
			Node replacement = ((Insertion) edit).replacement;
			if (replacement != null) return replacement;
		}
		return null;
	}

	private static void safeAdd(Map<Node, EditHint> map, Node node, EditHint edit) {
		safeAdd(map, node, edit, false);
	}

	private static void safeAdd(Map<Node, EditHint> map, Node node, EditHint edit, boolean defer) {
		if (defer && map.containsKey(node)) return;
		EditHint replaced = map.put(node, edit);
		if (replaced != null) {
			if (!(replaced instanceof Insertion && ((Insertion) replaced).replacement == node)) {
				System.err.println(node + ": " + replaced + " -> " + edit);
			}
		}
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
		}


		public void compareInserts(Node node, List<Insertion> a1, List<Insertion> a2,
				List<Insertion> b, boolean intersect) {
			compareInserts(node, intersect ? intersection(a1, a2) : union(a1, a2), b);
		}

		/** Assumes a and b have unique edits already */
		private static List<Insertion> union(List<Insertion> a, List<Insertion> b) {
			List<Insertion> all = new ArrayList<>();
			all.addAll(a);
			for (Insertion edit : b) {
				if (!contains(a, edit)) all.add(edit);
			}
			return all;
		}

		/** Assumes a and b have unique edits already */
		private static List<Insertion> intersection(List<Insertion> a, List<Insertion> b) {
			List<Insertion> all = new ArrayList<>();
			for (Insertion edit : a) {
				if (contains(b, edit)) all.add(edit);
			}
			return all;
		}

		private static boolean contains(List<Insertion> edits, Insertion edit) {
			for (Insertion e : edits) {
				if (equal(e, edit)) return true;
			}
			return false;
		}

		private static boolean equal(Insertion a, Insertion b) {
			if (a.index != b.index) return false;
			if (!a.type.equals(b.type)) return false;
			if (a.parent != b.parent) return false;
			return true;
		}

		public void compareEdit(Node node, EditHint editA, EditHint editB) {
			int indexA = getActionIndex(node, editA);
			int indexB = getActionIndex(node, editB);
			confusionMatrix[indexA][indexB]++;
		}

		public void compareEdit(Node node, EditHint editA1, EditHint editA2, EditHint editB,
				boolean intersect) {
			int indexA1 = getActionIndex(node, editA1);
			int indexA2 = getActionIndex(node, editA2);
			int indexB = getActionIndex(node, editB);
			int indexA = indexA1;
			if (intersect) {
				if (indexA1 != indexA2) indexA = 0;
			} else {
				if (indexA2 == indexB) indexA = indexA2;
			}
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
				// Replacements are essentially deletions
				Node replacement = ((Insertion) edit).replacement;
				if (replacement == node) return 1;
			}
			return 0;
		}

		public void print(String from, String to) {
			printCSV(from, to, null);
		}

		public void printCSV(String from, String to, CSVPrinter printer) {
			if (printer == null) System.out.println(from + " vs " + to);
			String[] labels = new String[] { "keep", "delete", "reorder", "move" };

			for (int i = 0; i < 4; i++) {
				if (i == 2) continue; // skip reorder
				int tp = 0, p = 0, l = 0;
				for (int j = 0; j < 4; j++) {
					p += confusionMatrix[i][j];
					l += confusionMatrix[j][i];
					if (i == j) tp += confusionMatrix[i][j];
				}
				printStats(printer, from, to, tp, l, p, labels[i]);
			}

			int tp = insertMatches[0], n = insertMatches[1],
					p = tp + n + insertMatches[2], l = tp + n + insertMatches[3];
			printStats(printer, from, to, tp, l, p, "insert");
		}

		public final static String[] CSV_HEADER = new String[] {
			"actual", "pred", "edit", "stat", "value", "correct", "total"
		};

		private static void printStats(CSVPrinter printer, String from, String to,
				int tp, int l, int p, String label) {
			double precision = (double)tp / l;
			double recall = (double)tp / p;
			if (printer == null) {
				String shortLabel = label.substring(0, 1);
				System.out.printf("R[%s]: %.02f (%d/%d)\tP[%s]: %.02f (%d/%d)\n",
						shortLabel, recall, tp, p,
						shortLabel, precision, tp, l);
			} else {
				try {
					printer.printRecord(from, to, label, "precision", precision, tp, l);
					printer.printRecord(from, to, label, "recall", recall, tp, p);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
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
