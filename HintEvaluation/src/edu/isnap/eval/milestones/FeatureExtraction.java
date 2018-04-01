package edu.isnap.eval.milestones;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class FeatureExtraction {

	public static void main(String[] args) {
		Assignment assignment = CSC200.Squiral;

		Map<AssignmentAttempt, List<Node>>  traceMap = assignment.load(
				Mode.Use, true, true, new SnapParser.SubmittedOnly()).values().stream()
				.collect(Collectors.toMap(
						attempt -> attempt,
						attempt -> attempt.rows.rows.stream()
						.map(action -> SimpleNodeBuilder.toTree(action.snapshot, true))
						.collect(Collectors.toList())));

		List<Node> correctSubmissions = traceMap.keySet().stream()
				.filter(attempt -> attempt.grade != null && attempt.grade.average() == 1)
				.map(attempt -> traceMap.get(attempt))
				.map(trace -> trace.get(trace.size() - 1))
				.collect(Collectors.toList());

		Map<PQGram, PQGramRule> rulesMap = new HashMap<>();
		for (int p = 1; p <= 3; p++) {
			for (int q = 1; q <= 4; q++) {
				for (Node node : correctSubmissions) {
					Set<PQGram> pqGrams = new HashSet<>(PQGram.extractFromNode(node, p, q));
					for (PQGram gram : pqGrams) {
						PQGramRule rule = rulesMap.get(gram);
						if (rule == null) {
							rulesMap.put(gram, rule = new PQGramRule(gram));
						}
						rule.followers.add(node.id);
					}
				}
			}
		}

		List<PQGramRule> rules = new ArrayList<>(rulesMap.values());
		removeDuplicateRulesAndSort(rules);

		System.out.println(correctSubmissions.size());
		rules.forEach(System.out::println);
	}

	private static void removeDuplicateRulesAndSort(List<PQGramRule> rules) {
		Collections.sort(rules);
		int maxFollowers = rules.get(rules.size() - 1).followCount();
		int nRules = rules.size();
		double[][] jaccardMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			for (int j = i + 1; j < nRules; j++) {
				jaccardMatrix[i][j] = jaccardMatrix[j][i] =
						rules.get(i).jaccardDistance(rules.get(j));
			}
		}
		List<PQGramRule> toRemove = new ArrayList<>();
		for (int i = 0; i < nRules; i++) {
			PQGramRule deleteCandidate = rules.get(i);
			if (deleteCandidate.followers.size() >= maxFollowers * 0.8) continue;
			for (int j = nRules - 1; j > i; j--) {
				PQGramRule supercedeCandidate = rules.get(j);
				if (deleteCandidate.jaccardDistance(supercedeCandidate) >= 0.975) {
					System.out.println("> " + deleteCandidate + "\n< " + supercedeCandidate);
					System.out.println();
					supercedeCandidate.duplicateRules.add(deleteCandidate);
					supercedeCandidate.duplicateRules.addAll(deleteCandidate.duplicateRules);
					toRemove.add(deleteCandidate);
					break;
				}
			}
		}
		rules.removeAll(toRemove);
	}


	public static class PQGramRule implements Comparable<PQGramRule> {

		public final PQGram pqGram;

		public final Set<String> followers = new HashSet<>();

		private final List<PQGramRule> duplicateRules = new ArrayList<>();

		public PQGramRule(PQGram pqGram) {
			this.pqGram = pqGram;
		}

		public int followCount() {
			return followers.size();
		}

		public int countIntersect(PQGramRule rule) {
			return (int) followers.stream().filter(rule.followers::contains).count();
		}

		public int countUnion(PQGramRule rule) {
			return (int) Stream.concat(followers.stream(), rule.followers.stream())
					.distinct().count();
		}

		public double jaccardDistance(PQGramRule rule) {
			int intersect = countIntersect(rule);
			return (double)intersect / (followCount() + rule.followCount() - intersect);
		}

		@Override
		public String toString() {
			return String.format("%03d: %s", followers.size(), pqGram);
		}


		@Override
		public int compareTo(PQGramRule o) {
			int fc = Integer.compare(followCount(), o.followCount());
			if (fc != 0) return fc;
			return pqGram.compareTo(o.pqGram);
		}
	}

	public static class PQGram implements Comparable<PQGram> {

		public final static String EMPTY = "*";

		public final String[] tokens;
		public final int p, q;

		private final int nEmpty;

		private PQGram(int p, int q, String[] tokens) {
			if (tokens.length != p + q) {
				throw new IllegalArgumentException("p + q must equal tokens.length");
			}

			this.p = p;
			this.q = q;
			this.tokens = tokens;

			nEmpty = (int) Arrays.stream(tokens).filter(EMPTY::equals).count();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (obj.getClass() != getClass()) return false;
			PQGram rhs = (PQGram) obj;
			return new EqualsBuilder()
					.append(p, rhs.p)
					.append(q, rhs.q)
					.append(tokens, rhs.tokens)
					.isEquals();
		}

		@Override
		public int hashCode() {
			// Could just cache this;
			return new HashCodeBuilder(5, 17)
					.append(p)
					.append(q)
					.append(tokens)
					.toHashCode();
		}

		@Override
		public String toString() {
			String[] out = Arrays.copyOf(tokens, tokens.length);
			out[p - 1] = "{" + out[p - 1]  + "}";
			return Arrays.toString(out);
		}

		private final static Comparator<PQGram> comparator =
				Comparator.comparing((PQGram gram) -> gram.p)
				.thenComparing(gram -> gram.q)
				.thenComparing(gram -> -gram.nEmpty);

		@Override
		public int compareTo(PQGram o) {
			return comparator.compare(this, o);
		}

		public static String labelForNode(Node node) {
			if (node == null) return EMPTY;
			return node.type();
		}

		public static List<PQGram> extractFromNode(Node node, int p, int q) {
			List<PQGram> list = new ArrayList<>();
			extractFromNode(node, p, q, list);
			return list;
		}

		private static void extractFromNode(Node node, int p, int q, List<PQGram> list) {
			String[] tokens = new String[p + q];
			Arrays.fill(tokens, EMPTY);

			Node parent = node;
			for (int i = p - 1; i >= 0; i--) {
				tokens[i] = labelForNode(parent);
				if (parent != null) parent = parent.parent;
			}

			if (node.children.size() == 0) {
				list.add(new PQGram(p, q, tokens));
				return;
			}

			for (int offset = 1 - q; offset <= node.children.size() - 1; offset++) {
				tokens = Arrays.copyOf(tokens, tokens.length);
				for (int i = 0; i < q; i++) {
					int tokenIndex = i + p;
					int childIndex = i + offset;
					Node child = childIndex >= 0 && childIndex < node.children.size() ?
							node.children.get(childIndex) : null;
					tokens[tokenIndex] = labelForNode(child);
				}
				list.add(new PQGram(p, q, tokens));
			}

			for (Node child : node.children) {
				extractFromNode(child, p, q, list);
			}
		}
	}

}
