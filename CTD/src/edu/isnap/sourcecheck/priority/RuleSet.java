package edu.isnap.sourcecheck.priority;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.isnap.ctd.hint.HintMap;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;
import edu.isnap.util.map.CountMap;
import edu.isnap.util.map.ListMap;

public class RuleSet implements Serializable {
	private static final long serialVersionUID = 1L;

	public final HintConfig config;
	private final List<Disjunction> decisions;

	public static PrintStream trace = NullStream.instance;

	@SuppressWarnings("unused")
	private RuleSet() { this(null, null); }

	public RuleSet(Collection<Node> solutions, HintConfig config) {
		this.config = config;
		this.decisions = findDecisions(solutions);
	}

	public List<Node> filterSolutions(Collection<Node> solutions, Node node) {
		if (!config.useRulesToFilter) return new ArrayList<>(solutions);

		Map<String, Node> idMap = solutions.stream().collect(Collectors.toMap(n -> n.id, n -> n));

		CountMap<String> countMap = getCountMap(node);

		decisions.stream().forEach(d -> d.printChoices(countMap));
		List<Set<String>> idSets = decisions.stream().map(d -> d.getAgreeingIDs(countMap))
				// Only count decisions with at least one agreeing ID
				.filter(s -> s.size() > 0)
				.collect(Collectors.toList());
		CountMap<String> matches = new CountMap<>();
		int max = 0;
		for (String id : idMap.keySet()) {
			int count = (int) idSets.stream().filter(set -> set.contains(id)).count();
			matches.put(id, count);
			max = Math.max(max, count);
		}
		List<String> sortedIDs = new ArrayList<>(idMap.keySet());
		sortedIDs.sort((o1, o2) -> -Integer.compare(matches.get(o1), matches.get(o2)));

		Set<String> bestIDs = new HashSet<>();
		int matchThreshhold = Integer.MAX_VALUE;
		for (String id : sortedIDs) {
			int nMatches = matches.get(id);
			if (bestIDs.size() < config.minRuleFilterSolutions || nMatches >= matchThreshhold) {
				bestIDs.add(id);
				matchThreshhold = nMatches;
//				trace.println(nMatches);
//				trace.println(id);
//				trace.println(idMap.get(id).prettyPrint());
			}
		}

		// Determine a percentage of the best rules that must follow a choice for the choice to be
		// considered plausible. Solution candidates that follow implausible choices are removed.
		for (Disjunction decision : decisions) {
			// For each decision, find any implausible choices
			for (Rule rule : decision.rules) {
				// A choice is never implausible if the student has chosen it
				if (rule.followedBy(countMap)) continue;
				int intersect = (int) rule.followers.stream().filter(bestIDs::contains).count();
				// TODO: config
				if (intersect > 0 && intersect < bestIDs.size() * 0.15) {
					bestIDs.removeAll(rule.followers);
					trace.printf("Implausible (%02d%%): %s\n",
							intersect * 100 / bestIDs.size(), rule);
				}
			}
		}
		trace.println("Filtered solutions: " + bestIDs.size() + "/" + solutions.size());

		return bestIDs.stream().map(id -> idMap.get(id)).collect(Collectors.toList());
	}

	private List<Disjunction> findDecisions(Collection<Node> solutions) {
		if (solutions == null) return null;

		int n = solutions.size();
		trace.println("N = " + n);
		int minThresh = (int) Math.ceil(n * config.ruleSupportThreshold);

		Map<String, CountMap<String>> supportMap = new TreeMap<>();
		ListMap<String, Integer> countMap = new ListMap<>();
		for (Node submission : solutions) {
			CountMap<String> counts = getCountMap(submission);
			for (String key : counts.keySet()) {
				countMap.add(key, counts.get(key));
			}
			String id = submission.id;
			if (id == null) continue;
			supportMap.put(submission.id, counts);

		}

		List<Rule> rules = new ArrayList<>();
		for (String key : countMap.keySet()) {
			List<Integer> counts = countMap.get(key);
			for (int count = 1; ; count++) {
				int fCount = count;
				int passing = (int) counts.stream().filter(c -> c >= fCount).count();
				// A rule isn't interesting if it applies to very little or everything
				if (passing >= minThresh) {
					if (passing < n) rules.add(new BaseRule(key, count));
				} else {
					// If we don't have the threshold at count, we won't at count + 1 either
					break;
				}
			}
		}

		for (String id : supportMap.keySet()) {
			CountMap<String> counts = supportMap.get(id);
			for (Rule rule : rules) {
				if (rule.followedBy(counts)) rule.followers.add(id);
				else rule.ignorers.add(id);
			}
		}

		removeDuplicateRulesAndSort(rules);
//		addConjunctions(rules);

		trace.println(); trace.println();
		rules.stream().forEach(trace::println);
		trace.println(); trace.println();

		return extractDecisions(rules);


		//		Spreadsheet out = new Spreadsheet();
		//		for (String id : submissions.keySet()) {
		//			out.newRow();
		////			out.put("id", id);
		//			for (int i = 0; i < rules.size(); i++) {
		//				out.put(rules.get(i).rootPath, rules.get(i).followers.contains(id));
		//			}
		//		}
		//		out.write(assignment.analysisDir() + "/rules.csv");


	}

	@SuppressWarnings("unused")
	private void addConjunctions(List<Rule> rules) {
		int nRules = rules.size();
		for (int i = 0; i < nRules; i++) {
			for (int j = i + 1; j < nRules; j++) {
				Rule ruleA = rules.get(i), ruleB = rules.get(j);
				Conjunction conj = new Conjunction();
				conj.addRule(ruleA); conj.addRule(ruleB);
				if (conj.support() >= config.ruleSupportThreshold &&
						// Ensure the rule contains a distinct subset of followers from its parent
						conj.followCount() <=
							Math.min(ruleA.followCount(), ruleB.followCount()) * 0.6) {
					rules.add(conj);
				}
			}
		}

		removeDuplicateRulesAndSort(rules);
	}

	private void removeDuplicateRulesAndSort(List<Rule> rules) {
		Collections.sort(rules);
		int nRules = rules.size();
		List<Rule> toRemove = new ArrayList<>();
		for (int i = 0; i < nRules; i++) {
			for (int j = nRules - 1; j > i; j--) {
				Rule deleteCandidate = rules.get(i);
				Rule supercedeCandidate = rules.get(j);
				if (deleteCandidate.jaccardDistance(supercedeCandidate) >= 0.975) {
					trace.println("> " + deleteCandidate + "\n< " + supercedeCandidate);
					trace.println();
					supercedeCandidate.duplicateRules.add(deleteCandidate);
					supercedeCandidate.duplicateRules.addAll(deleteCandidate.duplicateRules);
					toRemove.add(deleteCandidate);
					break;
				}
			}
		}
		rules.removeAll(toRemove);
	}

	private List<Disjunction> extractDecisions(List<Rule> sortedRules) {
		List<Disjunction> decisions = new ArrayList<>();
		for (int i = sortedRules.size() - 1; i >= 0; i--) {
			Rule startRule = sortedRules.get(i);
			// Don't start with a conjunction, just to reduce clutter
			if (!(startRule instanceof BaseRule)) continue;
			Disjunction disjunction = new Disjunction();
			disjunction.addRule(startRule);

			while (disjunction.support() < 1) {
				// TODO: config
				double bestRatio = 0.4;
				Rule bestRule = null;
				for (int j = i - 1; j >= 0; j--) {
					Rule candidate = sortedRules.get(j);
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
				trace.println(disjunction);
			}
		}
		return decisions;
	}

	private static CountMap<String> getCountMap(Node submission) {
		CountMap<String> counts = new CountMap<>();
		submission.recurse(new Action() {
			@Override
			public void run(Node node) {
				Node rootPath = HintMap.toRootPath(node).root();
				String key = makeKey(rootPath);
				counts.change(key, 1);
			}
		});
		return counts;
	}

//	public static Map<String, BaseRule> getRules(Node node) {
//		CountMap<String> countMap = getCountMap(node);
//		return countMap.keySet().stream().collect(Collectors.toMap(
//				k -> k,
//				k -> new BaseRule(k, countMap.get(k))));
//	}

	private static String makeKey(Node rootPath) {
		List<String> list = new ArrayList<>();
		while (rootPath.children.size() == 1) {
			list.add(rootPath.type());
			rootPath = rootPath.children.get(0);
		}
		list.add(rootPath.type());
		return list.toString();
	}


	static abstract class Rule implements Serializable, Comparable<Rule> {

		private static final long serialVersionUID = 1L;

		public final List<Rule> duplicateRules = new ArrayList<>();
		public final Set<String> followers = new HashSet<>();
		public final Set<String> ignorers = new HashSet<>();

		public abstract String name();
		protected abstract boolean followedBy(CountMap<String> countMap);

		protected int priority() {
			return 0;
		}

		public int followCount() {
			return followers.size();
		}

		public int ignoreCount() {
			return ignorers.size();
		}

		public double support() {
			return (double) followCount() / (followCount() + ignoreCount());
		}

		public int countIntersect(Rule rule) {
			return (int) followers.stream().filter(rule.followers::contains).count();
		}

		public int countUnion(Rule rule) {
			return (int) Stream.concat(followers.stream(), rule.followers.stream())
					.distinct().count();
		}

		public double jaccardDistance(Rule rule) {
			int intersect = countIntersect(rule);
			return (double)intersect / (followCount() + rule.followCount() - intersect);
		}

		@Override
		public int compareTo(Rule o) {
			int comp = Double.compare(this.support(), o.support());
			if (comp != 0) return comp;
			return Integer.compare(priority(), o.priority());
		}

		@Override
		public String toString() {
			return String.format("[%.02f] %s", support(), name());
		}
	}

	static class BaseRule extends Rule {
		private static final long serialVersionUID = 1L;

		public final String rootPath;
		public final int count;
		public final int depth;

		@SuppressWarnings("unused")
		private BaseRule() { this("", 0); }

		public BaseRule(String rootPath, int count) {
			this.rootPath = rootPath;
			// Count commas for depth
			this.depth = rootPath.length() - rootPath.replace(",", "").length();
			this.count = count;
		}

		@Override
		protected int priority() {
			// Base rules should take priority over others
			return 1;
		}

		@Override
		public String name() {
			return String.format("%d x %s", count, rootPath);
		}

		@Override
		public int compareTo(Rule o) {
			int sc = super.compareTo(o);
			if (sc != 0 || !(o instanceof BaseRule)) return sc;
			return -Integer.compare(depth, ((BaseRule) o).depth);
		}

		@Override
		public boolean followedBy(CountMap<String> countMap) {
			return countMap.getCount(rootPath) >= count ||
					duplicateRules.stream().anyMatch(r -> r.followedBy(countMap));
		}
	}

	static class Conjunction extends Rule {
		private static final long serialVersionUID = 1L;

		private List<Rule> rules = new ArrayList<>();

		public void addRule(Rule rule) {
			rules.add(rule);
			if (rules.size() == 1) {
				// If this is the first rule, set the followers equal to that rules'
				followers.addAll(rule.followers);
			} else {
				followers.retainAll(rule.followers);
			}
			ignorers.addAll(rule.ignorers);
		}

		@Override
		public String name() {
			return "(" + String.join(" AND ",
					rules.stream().map(r -> (CharSequence) r.name())::iterator) + ")";
		}

		@Override
		protected boolean followedBy(CountMap<String> countMap) {
			return rules.stream().allMatch(r -> r.followedBy(countMap));
		}

	}

	static class Disjunction extends Rule {
		private static final long serialVersionUID = 1L;

		private List<Rule> rules = new ArrayList<>();

		public void addRule(Rule rule) {
			rules.add(rule);
			followers.addAll(rule.followers);
			if (rules.size() == 1) {
				// If this is the first rule, set the ignorers equal to that rules'
				ignorers.addAll(rule.ignorers);
			} else {
				ignorers.retainAll(rule.ignorers);
			}
		}

		@Override
		public String name() {
			return "\t" + String.join(" OR\n\t\t",
					rules.stream().map(r -> (CharSequence) r.toString())::iterator);
		}

		public void printChoices(CountMap<String> countMap) {
			if (getAgreeingIDs(countMap).size() == 0) return;
			trace.println("Choices:");
			rules.stream().map(r -> (r.followedBy(countMap) ? "-> " : "   ") + r)
			.forEach(trace::println);
		}

		public Set<String> getAgreeingIDs(CountMap<String> countMap) {
			Set<String> ids = new HashSet<>();
			rules.stream().filter(r -> r.followedBy(countMap)).forEach(
					r -> ids.addAll(r.followers));
			return ids;

		}

		@Override
		protected boolean followedBy(CountMap<String> countMap) {
			return rules.stream().anyMatch(r -> r.followedBy(countMap));
		}

	}
}
