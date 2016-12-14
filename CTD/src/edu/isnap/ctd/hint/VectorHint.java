package edu.isnap.ctd.hint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.Canonicalization.InvertOp;
import edu.isnap.ctd.hint.Canonicalization.SwapArgs;
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
				if (c instanceof InvertOp || c instanceof SwapArgs) {
					swap = true;
					break;
				}
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
		public String data() {
			String data = "{";
			for (Entry<String, String> entry : dataMap().entrySet()) {
				if (data.length() > 1) data += ", ";
				data += String.format("\"%s\": %s", entry.getKey(), entry.getValue());
			}
			data += "}";
			return data;
		}

		protected Map<String, String> dataMap() {
			HashMap<String, String> map = new HashMap<>();
			map.put("root", getNodeReference(root));
			map.put("from", from.toJson(swapArgs));
			map.put("to", to.toJson(swapArgs));
			map.put("goal", goal.toJson(swapArgs));
			map.put("caution", String.valueOf(caution));
			return map;
		}

		protected static String getNodeReference(Node node) {
			if (node == null) return null;

			String label = node.type();
			for (Canonicalization c : node.canonicalizations) {
				if (c instanceof InvertOp) {
//					System.out.println("Invert: " + node);
					label = ((InvertOp) c).name;
					break;
				}
			}

			int index = node.index();
			if (node.parent != null) {
				for (Canonicalization c : node.parent.canonicalizations) {
					if (c instanceof SwapArgs) {
//						System.out.println("Swapping children of: " + node.parent);
						index = node.parent.children.size() - 1 - index;
						break;
					}
				}
			}

			String parent = getNodeReference(node.parent);

			return String.format("{\"label\": \"%s\", \"index\": %d, \"parent\": %s}",
					label, index, parent);
		}

		@Override
		protected String toCanonicalStringInternal() {
			return data();
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
			Node nRoot = root.copy(false);

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