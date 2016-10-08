package com.snap.data;

import java.util.Arrays;
import java.util.HashSet;

import org.w3c.dom.Element;

public class LiteralBlock extends Block {
	private static final long serialVersionUID = 1L;

	public final String value;
	public final boolean isVarRef;

	private final static HashSet<String> setVarBlocks = new HashSet<String>(Arrays.asList(new String[] {
		"doSetVar",
		"doChangeVar",
		"doShowVar",
		"doHideVar"
	}));

	@Override
	public String name(boolean canon) {
		return canon ? type() : value;
	}

	@Override
	public String type() {
		return isVarRef ? "literal" : "varDec";
	}

	@SuppressWarnings("unused")
	private LiteralBlock() {
		this(null, null, null, false);
	}

	public LiteralBlock(String type, String id, String value, boolean isVarRef) {
		super(type, id);
		this.value = value;
		this.isVarRef = isVarRef;
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
