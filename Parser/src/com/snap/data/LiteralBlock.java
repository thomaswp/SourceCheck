package com.snap.data;

import java.util.Arrays;
import java.util.HashSet;

import org.w3c.dom.Element;

public class LiteralBlock extends Block {
	private static final long serialVersionUID = 1L;

	public enum Type {
		Text(null), VarRef(null), Option("option"), Color("color");

		public final String tag;

		Type(String tag) {
			this.tag = tag;
		}

		boolean isOfType(String value) {
			return tag != null && value.startsWith("<" + tag + ">") &&
					value.endsWith("</" + tag + ">");
		}

		String parse(String value) {
			return value.substring(tag.length() + 2, value.length() - tag.length() - 3);
		}
	}

	public final String value;
	public final Type type;

	private final static HashSet<String> setVarBlocks = new HashSet<String>(Arrays.asList(new String[] {
		"doSetVar",
		"doChangeVar",
		"doShowVar",
		"doHideVar"
	}));

	@Override
	public String name(boolean canon) {
		return canon ? "literal" : value;
	}

	@Override
	public String type() {
		return type == Type.VarRef ? "var" : "literal";
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
			_value = value;
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
		if (parent.getChildNodes().item(0) == element) {
			isVarRef = setVarBlocks.contains(parent.getAttribute("s"));
		}
		return new LiteralBlock(
				element.getTagName(),
				getID(element),
				element.getTextContent(),
				isVarRef);
	}

	@Override
	public String toCode(boolean canon) {
		return name(canon);
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) { }
}
