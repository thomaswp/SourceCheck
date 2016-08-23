package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Script extends Code implements IHasID {
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
	public String toCode(boolean canon) {
		 return new CodeBuilder(canon)
		 .indent()
		 .add(blocks)
		 .end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(blocks);
		return "script";
	}
	
	@Override
	public String getID() {
		if (blocks.size() == 0) return null;
		String id = "";
		for (Block block : blocks) {
			if (id.length() == 0) id += ",";
			id += block.getID();
		}
		return id;
	}
}
