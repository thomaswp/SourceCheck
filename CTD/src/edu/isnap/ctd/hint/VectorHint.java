package edu.isnap.ctd.hint;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.Canonicalization.SwapBinaryArgs;
import edu.isnap.ctd.util.StringHashable;

public class VectorHint extends StringHashable implements Hint {

		public final Node root;
		public final String rootPathString;
		public final VectorState from, to, goal;
		public final boolean caution;

		protected final boolean swapArgs;

		public VectorHint(Node root, String rootPathString, VectorState from, VectorState to,
				VectorState goal, boolean caution) {
			this.root = root;
			this.rootPathString = rootPathString;
			this.from = from;
			this.to = to;
			this.goal = goal;
			this.caution = caution;

			boolean swap = false;
			for (Canonicalization c : root.canonicalizations) {
				if (c instanceof SwapBinaryArgs) {
					swap = true;
					break;
				}
				// We don't have to worry about Reorder canonicalizations because then the order of
				// children should be irrelevant
			}
			this.swapArgs = swap;
		}

		@Override
		protected boolean autoCache() {
			return true;
		}

		@Override
		public String from() {
			return rootPathString + ": " + from;
		}

		@Override
		public String to() {
			return rootPathString + ": " + to;
		}

		@Override
		public JSONObject data() {
			JSONObject data = new JSONObject();
			data.put("root", Node.getNodeReference(root));
			data.put("from", from.toJSON(swapArgs));
			data.put("to", to.toJSON(swapArgs));
			data.put("goal", goal.toJSON(swapArgs));
			data.put("caution", caution);
			return data;
		}

		@Override
		protected String toCanonicalStringInternal() {
			return data().toString();
		}

		public Node outcome() {
			return applyHint(root, to.items);
		}

		public VectorState getMissingChildren() {
			List<String> missing = new LinkedList<>();
			for (String i : goal.items) missing.add(i);
			for (String i : from.items) missing.remove(i);
			return new VectorState(missing);
		}

		public static Node applyHint(Node root, String[] to) {
			Node nRoot = root.copy();

			List<Node> children = new ArrayList<>();
			children.addAll(nRoot.children);

			nRoot.children.clear();
			for (String type : to) {
				boolean added = false;
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i).hasType(type)) {
						added = true;
						nRoot.children.add(children.remove(i));
						break;
					}
				}
				if (!added) {
					nRoot.children.add(new Node(nRoot, type));
				}
			}

			return nRoot;
		}

		@Override
		public String type() {
			return "vector";
		}

	}