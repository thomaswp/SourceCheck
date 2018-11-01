package edu.isnap.feature;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;

import edu.isnap.node.Node;

public class PQGram implements Comparable<PQGram> {

	public final static String EMPTY = "*";

	public final String[] tokens;
	public final int p, q;
	public int count = 1;

	private final int nEmpty;
	private final int nonEmptyP, nonEmptyQ;

	private PQGram() {
		this(0, 0, new String[0]);
	}

	private PQGram(int p, int q, String[] tokens) {
		if (tokens.length != p + q) {
			throw new IllegalArgumentException("p + q must equal tokens.length");
		}

		this.p = p;
		this.q = q;
		this.tokens = tokens;

		nEmpty = (int) Arrays.stream(tokens).filter(EMPTY::equals).count();
		int pr = 0, qr = 0;
		for (int i = 0 ; i < tokens.length; i++) {
			if (EMPTY.equals(tokens[i])) continue;
			if (i < p) pr++;
			else if (i > p) qr++;
		}
		this.nonEmptyP = pr;
		this.nonEmptyQ = qr;
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
				.append(count, rhs.count)
				.append(tokens, rhs.tokens)
				.isEquals();
	}

	@Override
	public int hashCode() {
		// Could just cache this;
		return new HashCodeBuilder(5, 17)
				.append(p)
				.append(q)
				.append(count)
				.append(tokens)
				.toHashCode();
	}

	@Override
	public String toString() {
		String[] out = Arrays.copyOf(tokens, tokens.length);
		out[p - 1] = "{" + out[p - 1]  + "}";
		return Arrays.toString(out) + (count > 1 ? (" x" + count) : "");
	}

	private final static Comparator<PQGram> comparator =
			Comparator.comparing((PQGram gram) -> gram.nonEmptyQ)
			.thenComparing(gram -> gram.nonEmptyP)
			.thenComparing(gram -> gram.count)
			.thenComparing(gram -> -gram.nEmpty)
			.thenComparing(gram -> gram.tokens[gram.p - 1]);

	@Override
	public int compareTo(PQGram o) {
		return comparator.compare(this, o);
	}

	// Not currently in use...
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

	public static Set<PQGram> extractAllFromNode(Node node) {
		Set<PQGram> pqGrams = new HashSet<>();
		for (int p = 3; p > 0; p--) {
			for (int q = 4; q > 0; q--) {
				pqGrams.addAll(extractFromNode(node, p, q));
			}
		}
		return pqGrams;
	}

	public static Set<PQGram> extractFromNode(Node node, int p, int q) {
		Set<PQGram> set = new HashSet<>();
		extractFromNode(node, p, q, set);
		return set;
	}

	private static void extractFromNode(Node node, int p, int q, Set<PQGram> set) {
		String[] tokens = new String[p + q];
		Arrays.fill(tokens, EMPTY);

		Node parent = node;
		for (int i = p - 1; i >= 0; i--) {
			tokens[i] = labelForNode(parent);
			if (parent != null) parent = parent.parent;
		}

		if (node.children.size() == 0) {
			set.add(new PQGram(p, q, tokens));
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
			PQGram pqGram = new PQGram(p, q, tokens);
			while (!set.add(pqGram)) pqGram.count++;
		}

		for (Node child : node.children) {
			extractFromNode(child, p, q, set);
		}
	}
}