package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class CallBlock extends Block {
	private static final long serialVersionUID = 1L;
	
	public final List<Block> parameters = new ArrayList<Block>();
	public final List<Script> bodies = new ArrayList<Script>();
	public final boolean isCustom;

	@SuppressWarnings("unused")
	private CallBlock() {
		this(null, false);
	}
	
	public CallBlock(String type, boolean isCustom) {
		super(type);
		this.isCustom = isCustom;
	}
	
	public static Block parse(Element element) {
		if (element.hasAttribute("var")) {
			return new VarBlock(element.getAttribute("var"));
		}
		CallBlock block = new CallBlock(element.getAttribute("s").replace("%n", "%s"), element.getTagName().equals("custom-block"));
		for (Code code : XML.getCode(element)) {
			if (code instanceof Block) {
				block.parameters.add((Block) code);
			} else if (code instanceof Script) {
				block.bodies.add((Script) code);
			} else {
				throw new RuntimeException("Unknown code in call block: " + code);
			}
		}
		return block;
	}

	@Override
	public String toCode() {
		return new CodeBuilder()
		.add(name)
		.addParameters(parameters)
		.add(bodies.size() > 0 ? " " : "")
		.add(bodies)
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(parameters);
		ac.add(bodies);
		return name;
	}
}
