package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Script extends Code implements IHasID {
	private static final long serialVersionUID = 1L;

	public final List<Block> blocks = new ArrayList<Block>();
	public final String customBlockID;

	public Script() {
		this(null);
	}

	public Script(String customBlockID) {
		this.customBlockID = customBlockID;
	}

	public static Script parse(Element element) {
		return parse(element, null);
	}

	public static Script parse(Element element, String customBlockID) {
		Script script = new Script(customBlockID);
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
		if (customBlockID != null) return customBlockID;
		if (blocks.size() == 0) return null;
		String id = "script:";
		for (Block block : blocks) {
			if (id.length() == 0) id += ",";
			id += block.getID();
		}
		return id;
	}
}
