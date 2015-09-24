package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Script extends Code {
	private static final long serialVersionUID = 1L;
	
	public final List<Block> blocks = new ArrayList<Block>();

	public static Script parse(Element element) {
		Script script = new Script();
		for (Code code : XML.getCode(element)) {
			script.blocks.add((Block) code);
		}
		return script;
	}

	@Override
	public String toCode() {
		 return new CodeBuilder()
		 .indent()
		 .add(blocks)
		 .end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(blocks);
		return "script";
	}
}
