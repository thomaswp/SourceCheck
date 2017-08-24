package edu.isnap.eval.rules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class FindRules {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		Assignment assignment = CSC200.Squiral;
		double threshold = 0.05;

		Map<String, Node> submissions = assignment.load(Mode.Use, true, true,
				new SnapParser.LikelySubmittedOnly()).values().stream()
				.filter(a -> a.submittedSnapshot != null && a.grade != null &&
						a.grade.average() == 1)
				.collect(Collectors.toMap(
						a -> a.id.substring(0, 8),
						a -> SimpleNodeBuilder.toTree(a.submittedSnapshot, true)));

		int n = submissions.size();
		System.out.println(assignment.name);
		System.out.println("N = " + n);
		int minThresh = (int) Math.ceil(n * threshold);

		Map<String, CountMap<String>> supportMap = new TreeMap<>();
		ListMap<String, Integer> countMap = new ListMap<>();
		for (String submittedID : submissions.keySet()) {
			Node submission = submissions.get(submittedID);
			CountMap<String> counts = getCountMap(submission);
			for (String key : counts.keySet()) {
				countMap.add(key, counts.get(key));
			}
			supportMap.put(submittedID, counts);

		}

		List<BaseRule> rules = new ArrayList<>();
		for (String key : countMap.keySet()) {
			List<Integer> counts = countMap.get(key);
			for (int count = 1; ; count++) {
				int fCount = count;
				int passing = (int) counts.stream().filter(c -> c >= fCount).count();
				if (passing >= minThresh) rules.add(new BaseRule(key, count));
				else break;
			}
		}

		for (String id : supportMap.keySet()) {
			CountMap<String> counts = supportMap.get(id);
			for (BaseRule rule : rules) {
				if (counts.getCount(rule.rootPath) >= rule.count) rule.followers.add(id);
				else rule.ignorers.add(id);
			}
		}

		Collections.sort(rules);

		int nRules = rules.size();
		double[][] jaccardMatrix = new double[nRules][nRules];
		for (int i = 0; i < nRules; i++) {
			for (int j = i + 1; j < nRules; j++) {
				jaccardMatrix[i][j] = jaccardMatrix[j][i] =
						rules.get(i).jaccardDistance(rules.get(j));
			}
		}
		List<BaseRule> toRemove = new ArrayList<>();
		for (int i = 0; i < nRules; i++) {
			for (int j = nRules - 1; j > i; j--) {
				if (rules.get(i).jaccardDistance(rules.get(j)) >= 0.975) {
					System.out.println("> " + rules.get(i) + "\n< " + rules.get(j));
					System.out.println();
					toRemove.add(rules.get(i));
					break;
				}
			}
		}
		rules.removeAll(toRemove);
		nRules = rules.size();

		//		Spreadsheet out = new Spreadsheet();
		//		for (String id : submissions.keySet()) {
		//			out.newRow();
		////			out.put("id", id);
		//			for (int i = 0; i < rules.size(); i++) {
		//				out.put(rules.get(i).rootPath, rules.get(i).followers.contains(id));
		//			}
		//		}
		//		out.write(assignment.analysisDir() + "/rules.csv");


		System.out.println(); System.out.println();
		rules.stream().forEach(System.out::println);
		System.out.println(); System.out.println();

		for (int i = nRules - 1; i >= 0; i--) {
			Disjunction disjunction = new Disjunction();
			disjunction.addRule(rules.get(i));

			while (disjunction.support() < 1) {
				double bestRatio = 0.4;
				BaseRule bestRule = null;
				for (int j = i - 1; j >= 0; j--) {
					BaseRule candidate = rules.get(j);
					int intersect = disjunction.countIntersect(candidate);
					double ratio = (double) intersect / candidate.followCount();
					if (ratio < bestRatio) {
						bestRatio = ratio;
						bestRule = candidate;
					}
				}

				if (bestRule == null) break;
				disjunction.addRule(bestRule);
			}
			if (disjunction.support() >= 0.9) System.out.println(disjunction);
		}

		//		for (int i = 0; i < nRules; i++) {
		//			for (int j = i + 1; j < nRules; j++) {
		//				Rule ruleA = rules.get(i), ruleB = rules.get(j);
		//				int smaller = Math.min(ruleA.followCount(), ruleB.followCount());
		//				int intersect = ruleA.countIntersect(ruleB);
		//				int union = ruleA.countUnion(ruleB);
		//
		//				if (intersect < smaller * 0.25 && union >= n * 0.75) {
		//					System.out.printf("%d%% (%02d): %s\n          %s\n",
		//							(union * 100 / n), intersect, ruleA, ruleB);
		//				}
		//			}
		//		}
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

	public static Map<String, BaseRule> getRules(Node node) {
		CountMap<String> countMap = getCountMap(node);
		return countMap.keySet().stream().collect(Collectors.toMap(
				k -> k,
				k -> new BaseRule(k, countMap.get(k))));
	}

	public static Set<String> getCompatibleIDs(Node node, Set<String> ids,
			List<Disjunction> choiceSets, int minCount) {
		CountMap<String> countMap = getCountMap(node);

		List<Set<String>> idSets = choiceSets.stream().map(d -> d.getAgreeingIDs(countMap))
				.collect(Collectors.toList());
		CountMap<String> matches = new CountMap<>();
		int max = 0;
		for (String id : ids) {
			int count = (int) idSets.stream().filter(set -> set.contains(id)).count();
			matches.put(id, count);
			max = Math.max(max, count);
		}
		List<String> sortedIDs = new ArrayList<>(ids);
		sortedIDs.sort((o1, o2) -> -Integer.compare(matches.get(o1), matches.get(o2)));

		Set<String> bestIDs = new HashSet<>();
		int matchThreshhold = Integer.MAX_VALUE;
		for (String id : sortedIDs) {
			int nMatches = matches.get(id);
			if (bestIDs.size() < minCount || nMatches >= matchThreshhold) {
				bestIDs.add(id);
				matchThreshhold = nMatches;
			}
		}

		return bestIDs;

	}

	private static String makeKey(Node rootPath) {
		List<String> list = new ArrayList<>();
		while (rootPath.children.size() == 1) {
			list.add(rootPath.type());
			rootPath = rootPath.children.get(0);
		}
		list.add(rootPath.type());
		return list.toString();
	}

	static abstract class Rule implements Comparable<Rule> {

		public final Set<String> followers = new HashSet<>();
		public final Set<String> ignorers = new HashSet<>();

		public abstract String name();
		protected abstract boolean follows(CountMap<String> countMap);

		public int followCount() {
			return followers.size();
		}

		public int ignoreCount() {
			return ignorers.size();
		}

		public double support() {
			return (double) followCount() / (followCount() + ignoreCount());
		}

		public int countIntersect(BaseRule rule) {
			return (int) followers.stream().filter(rule.followers::contains).count();
		}

		public int countUnion(BaseRule rule) {
			return (int) Stream.concat(followers.stream(), rule.followers.stream())
					.distinct().count();
		}

		public double jaccardDistance(BaseRule rule) {
			int intersect = countIntersect(rule);
			return (double)intersect / (followCount() + rule.followCount() - intersect);
		}

		@Override
		public int compareTo(Rule o) {
			return Double.compare(this.support(), o.support());
		}

		@Override
		public String toString() {
			return String.format("[%.02f] %s", support(), name());
		}
	}

	static class BaseRule extends Rule {
		public final String rootPath;
		public final int count;
		public final int depth;

		public BaseRule(String rootPath, int count) {
			this.rootPath = rootPath;
			// Count commas for depth
			this.depth = rootPath.length() - rootPath.replace(",", "").length();
			this.count = count;
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

		public boolean follows(BaseRule rule) {
			return rule.rootPath.equals(rootPath) && rule.count >= count;
		}

		@Override
		public boolean follows(CountMap<String> countMap) {
			Integer count = countMap.get(rootPath);
			return count != null && count >= count;
		}
	}

	static class Disjunction extends Rule {

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
			return "\t" + String.join(" OR \n\t\t",
					rules.stream().map(r -> (CharSequence) r.toString())::iterator);
		}

		public Set<String> getAgreeingIDs(CountMap<String> countMap) {
			Set<String> ids = new HashSet<>();
			rules.stream().filter(r -> r.follows(countMap)).forEach(r -> ids.addAll(r.followers));
			return ids;

		}

		@Override
		protected boolean follows(CountMap<String> countMap) {
			return rules.stream().anyMatch(r -> r.follows(countMap));
		}

	}
}
