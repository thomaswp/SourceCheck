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

import org.apache.commons.lang.StringUtils;
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


		int n = correctSubmissions.size();
		System.out.println(n);

		Map<PQGram, PQGramRule> rulesMap = new HashMap<>();
		for (Node node : correctSubmissions) {
			for (PQGram gram : extractPQGrams(node)) {
				PQGramRule rule = rulesMap.get(gram);
				if (rule == null) {
					rulesMap.put(gram, rule = new PQGramRule(gram, n));
				}
				rule.followers.add(node.id);
			}
		}



		List<PQGramRule> rules = new ArrayList<>(rulesMap.values());
		rules.removeIf(rule -> rule.followers.size() < n * 0.2);
		rulesMap.keySet().retainAll(rules);
		Collections.sort(rules);

		List<List<Node>> allTraces = new ArrayList<>(traceMap.values());

		int nSnapshots = allTraces.stream().mapToInt(list -> list.size()).sum();
		rules.forEach(rule -> rule.snapshotVector = new byte[nSnapshots]);

		int snapshotIndex = 0;
		for (List<Node> trace : allTraces) {
			for (Node snapshot : trace) {
				for (PQGram gram : extractPQGrams(snapshot)) {
					PQGramRule rule = rulesMap.get(gram);
					if (rule == null) continue;
					rule.snapshotVector[snapshotIndex] = 1;
				}
				snapshotIndex++;
				if (snapshotIndex % (nSnapshots / 50) == 0) System.out.print("+");
			}
		}
		System.out.println();
		if (snapshotIndex != nSnapshots) throw new RuntimeException();
		rules.forEach(rule -> rule.calculateSnapshotCount());

		removeDuplicateRules(rules);

		System.out.println("Rules: ");
		rules.stream().filter(rule -> rule.support() >= 0).forEach(System.out::println);

		System.out.println("Decisions: ");
		List<Disjunction> decisions = extractDecisions(rules);
		decisions.forEach(System.out::println);



	}

	private static Set<PQGram> extractPQGrams(Node node) {
		Set<PQGram> pqGrams = new HashSet<>();
		for (int p = 3; p > 0; p--) {
			for (int q = 4; q > 0; q--) {
				pqGrams.addAll(PQGram.extractFromNode(node, p, q));
			}
		}
		return pqGrams;
	}

	private static void removeDuplicateRules(List<? extends Rule> rules) {
		int maxFollowers = rules.get(0).snapshotVector.length;
		int nRules = rules.size();

		double[][] jaccardMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			Rule ruleA = rules.get(i);
			byte[] fA = ruleA.snapshotVector;
			for (int j = i + 1; j < nRules; j++) {
				Rule ruleB = rules.get(j);
				byte[] fB = ruleB.snapshotVector;
				int intersect = 0;
				for (int k = 0; k < maxFollowers; k++) {
					if (fA[k] == 1 && fB[k] == 1) intersect++;
				}
				jaccardMatrix[i][j] =
						(double)intersect / (ruleA.snapshotCount + ruleB.snapshotCount - intersect);
			}
		}
		List<Rule> toRemove = new ArrayList<>();
		for (int i = 0; i < nRules; i++) {
			Rule deleteCandidate = rules.get(i);

			// Remove any rule with a very high support, since these will be trivially true
			if (deleteCandidate.snapshotCount >= maxFollowers * 0.95) {
				toRemove.add(deleteCandidate);
				System.out.println("-- " + deleteCandidate);
				System.out.println();
				continue;
			}

			for (int j = nRules - 1; j > i; j--) {
				Rule supercedeCandidate = rules.get(j);
				if (jaccardMatrix[i][j] >= 0.975) {
					System.out.println("- " + deleteCandidate + "\n+ " + supercedeCandidate);
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

	private static List<Disjunction> extractDecisions(List<PQGramRule> sortedRules) {
		List<Disjunction> decisions = new ArrayList<>();
		for (int i = sortedRules.size() - 1; i >= 0; i--) {
			PQGramRule startRule = sortedRules.get(i);
			Disjunction disjunction = new Disjunction(startRule);

			while (disjunction.support() < 1) {
				// TODO: config
				double bestRatio = 0.4;
				PQGramRule bestRule = null;
				for (int j = i - 1; j >= 0; j--) {
					PQGramRule candidate = sortedRules.get(j);
					int intersect = disjunction.countIntersect(candidate);
					// TODO: config
					// Ignore tiny overlap - there can always be a fluke
					if (intersect <= candidate.followCount() * 0.15) intersect = 0;
					double ratio = (double) intersect / candidate.followCount();
					if (ratio < bestRatio) {
						bestRatio = ratio;
						bestRule = candidate;
					}
					if (ratio == 0) break;
				}

				if (bestRule == null) break;
				disjunction.addRule(bestRule);
			}
			// TODO: config
			// We only want high-support decisions that have multiple choices
			if (disjunction.support() >= 0.9 && disjunction.rules.size() > 1) {
				decisions.add(disjunction);
			}
		}
		return decisions;
	}

	public static abstract class Rule {
		public final Set<String> followers = new HashSet<>();
		public final int maxFollowers;

		protected final List<Rule> duplicateRules = new ArrayList<>();

		protected byte[] snapshotVector;
		protected int snapshotCount;

		public Rule(int maxFollowers) {
			this.maxFollowers = maxFollowers;
		}

		public void calculateSnapshotCount() {
			int count = 0;
			for (int j = 0; j < snapshotVector.length; j++) {
				count += snapshotVector[j];
			}
			snapshotCount = count;
		}

		public int followCount() {
			return followers.size();
		}

		public double support() {
			return (double) followers.size() / maxFollowers;
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
	}

	public static class PQGramRule extends Rule implements Comparable<PQGramRule> {

		public final PQGram pqGram;

		public PQGramRule(PQGram pqGram, int maxFollowers) {
			super(maxFollowers);
			this.pqGram = pqGram;
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

	static class Disjunction extends Rule {

		private List<PQGramRule> rules = new ArrayList<>();

		public Disjunction(PQGramRule startRule) {
			super(startRule.maxFollowers);
			snapshotVector = new byte[startRule.snapshotVector.length];
			addRule(startRule);
		}

		public void addRule(PQGramRule rule) {
			rules.add(rule);
			followers.addAll(rule.followers);
			for (int i = 0; i < snapshotVector.length; i++) {
				if (rule.snapshotVector[i] == 1) snapshotVector[i] = 1;
			}
			calculateSnapshotCount();
		}

		@Override
		public int countIntersect(PQGramRule rule) {
			return (int) followers.stream().filter(rule.followers::contains).count();
		}

		@Override
		public String toString() {
			return "\t" + String.join(" OR\n\t\t",
					rules.stream().map(r -> (CharSequence) r.toString())::iterator);
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
				Comparator.comparing((PQGram gram) -> gram.q)
				.thenComparing(gram -> gram.p)
				.thenComparing(gram -> -gram.nEmpty)
				.thenComparing(gram -> gram.tokens[gram.p - 1]);

		@Override
		public int compareTo(PQGram o) {
			return comparator.compare(this, o);
		}

		public boolean contains(PQGram o) {
			if (o.q > q) return false;
			if (o.p > p + 1) return false;
			if (o.p > p && o.q > 1) return false;

			if (o.q == 1) {
				// If the the other is a path, check if it is a subsequence of our p
				if (containsSubsequece(tokens, 0, p, o.tokens, 0, o.tokens.length)) return true;
			}

			// Next make sure that other's p is a subset of ours
			for (int i = 0; i < p && i < o.p; i++) {
				if (!StringUtils.equals(tokens[p - i - 1], o.tokens[o.p - i - 1])) return false;
			}

			// Then make sure the other's q is a subsequence of ours
			return containsSubsequece(tokens, p, q, o.tokens, o.p, o.q);
		}

		private static boolean containsSubsequece(String[] a, int startA, int lengthA,
				String[] b, int startB, int lengthB) {
			if (lengthB > lengthA) return false;

			for (int offset = 0; offset <= lengthA - lengthB; offset++) {
				boolean eq = true;
				for (int i = 0; i < lengthB; i++) {
					if (!StringUtils.equals(a[startA + i + offset], b[startB + i])) {
						eq = false;
						break;
					}
				}
				if (eq) return true;
			}
			return false;
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
