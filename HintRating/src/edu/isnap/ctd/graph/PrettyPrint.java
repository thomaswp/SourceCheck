package edu.isnap.ctd.graph;

import java.util.List;
import java.util.function.Predicate;

public class PrettyPrint {

	public static String print(INode node) {
		return print(node, "", Params.Default);
	}

	public static String toString(INode node, Params params) {
		return print(node, "", params);
	}

	private  static String print(INode node, String indent, Params params) {
		if (node == null) return "";
		String out = params.baseString(node);
		boolean inline = !params.isBodyType.test(node.type());
		List<? extends INode> children = node.children();
		if (children.size() > 0) {
			if (inline) {
				out += "(";
				for (int i = 0; i < children.size(); i++) {
					if (i > 0) out += ", ";
					if (children.get(i) == null) {
						out += "null";
						continue;
					}
					out += print(children.get(i), indent, params);
				}
				out += ")";
			} else {
				out += " {\n";
				String indentMore = indent;
				for (int i = 0; i < params.indent ; i++) indentMore += " ";
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i) == null) {
						out += indentMore + "null\n";
						continue;
					}
					out += indentMore + print(children.get(i),
							indentMore, params) + "\n";
				}
				out += indent + "}";
			}
		}
		return out;

	}

	public static class Params {

		public final static Params Default = new Params();

		public int indent = 2;
		public boolean showValues = true;
		public Predicate<String> isBodyType = s -> false;
		public boolean surroundValueAssignments = true;
		public boolean backquoteValuesWithWhitespace = true;
		public String valueAssignment = "=";

		public String baseString(INode node) {
			StringBuilder sb = new StringBuilder();
			sb.append(node.type());
			String value = node.value();
			if (showValues && value != null) {
				if (surroundValueAssignments) sb.insert(0, "[");
				sb.append(valueAssignment);
				boolean quote = backquoteValuesWithWhitespace && value.matches(".*\\s.*");
				if (quote) sb.append("`");
				sb.append(value);
				if (quote) sb.append("`");
				if (surroundValueAssignments) sb.append("]");
			}
			return sb.toString();
		}

	}
}
