package com.snap.data;

import java.util.Arrays;
import java.util.HashSet;

import org.w3c.dom.Element;

public class LiteralBlock extends Block {
	public final String value;
	public final boolean isVarRef;
	
	private final static HashSet<String> setVarBlocks = new HashSet<String>(Arrays.asList(new String[] {
		"doSetVar",
		"doChangeVar",
		"doShowVar",
		"doHideVar"
	}));
	
	public LiteralBlock(String type, String value, boolean isVarRef) {
		super(type);
		this.value = value;
		this.isVarRef = isVarRef;
	}

	public static LiteralBlock parse(Element element) {
		Element parent = (Element) element.getParentNode();
		boolean isVarRef = false;
		if (parent.getChildNodes().item(0) == element) {
			isVarRef = setVarBlocks.contains(parent.getAttribute("s"));
		}
		return new LiteralBlock(element.getTagName(), element.getTextContent(), isVarRef);
	}
	
	@Override
	public String toCode() {
		return value;
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		return canon ? "literal" : value;
	}
}
