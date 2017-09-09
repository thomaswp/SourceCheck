package edu.isnap.parser.elements;

import java.util.Arrays;
import java.util.HashSet;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import edu.isnap.parser.elements.util.XML;

public class LiteralBlock extends Block {
	private static final long serialVersionUID = 1L;

	public enum Type {
		Text(null), VarRef(null), Option("option"), Color("color");

		public final String tag;

		Type(String tag) {
			this.tag = tag;
		}

		boolean isOfType(String value) {
			return value != null && tag != null && value.startsWith("<" + tag + ">") &&
					value.endsWith("</" + tag + ">");
		}

		String parse(String value) {
			return value.substring(tag.length() + 2, value.length() - tag.length() - 3);
		}
	}

	public final String value;
	public final Type type;

	private final static HashSet<String> setVarBlocks = new HashSet<>(Arrays.asList(new String[] {
		"doSetVar",
		"doChangeVar",
		"doShowVar",
		"doHideVar"
	}));
	private final static HashSet<String> setVarInListBlocks =
			new HashSet<>(Arrays.asList(new String[] {
		"doDeclareVariables",
	}));

	@Override
	public String type() {
		return type == Type.VarRef ? "var" : "literal";
	}

	@Override
	public String value() {
		return value;
	}

	@SuppressWarnings("unused")
	private LiteralBlock() {
		this(null, null, null, false);
	}

	public LiteralBlock(String name, String id, String value, boolean isVarRef) {
		super(name, id);

		Type _type = Type.Text;
		String _value = value;

		if (isVarRef) {
			_type = Type.VarRef;
		} else {
			for (Type type : Type.values()) {
				if (type.isOfType(value)) {
					_type = type;
					_value = type.parse(value);
				}
			}
		}

		this.type = _type;
		this.value = _value;
	}

	public static LiteralBlock parse(Element element) {
		Element parent = (Element) element.getParentNode();
		boolean isVarRef = false;
		if (parent != null) {
			if (XML.getFirstChild(parent) == element) {
				isVarRef = setVarBlocks.contains(parent.getAttribute("s"));
			}
			if ("list".equals(parent.getTagName())) {
				Node grandparent = parent.getParentNode();
				if (grandparent != null && grandparent instanceof Element) {
					if (XML.getFirstChild((Element) grandparent) == parent) {
						isVarRef = setVarInListBlocks.contains(
								((Element) grandparent).getAttribute("s"));
					}
				}
			}
		}
		return new LiteralBlock(
				element.getTagName(),
				getID(element),
				element.getTextContent(),
				isVarRef);
	}

	@Override
	public String toCode(boolean canon) {
		return value();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) { }
}
