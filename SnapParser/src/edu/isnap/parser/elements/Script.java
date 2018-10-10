package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.parser.elements.util.XML;

public class Script extends Code implements IHasID, Comparable<Script> {
	private static final long serialVersionUID = 1L;

	public final List<Block> blocks = new ArrayList<>();
	public final String customBlockID;

	@Override
	public String type() {
		return "script";
	}

	@Override
	public String value() {
		return null;
	}

	public Script() {
		this(null);
	}

	public Script(String customBlockID) {
		this.customBlockID = customBlockID;
	}

	public static Script parse(Element element) {
		Script script = new Script(null);
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
	public void addChildren(boolean canon, Accumulator ac) {
		ac.add(blocks);
	}

	@Override
	public String getID() {
		// TODO: ID should really be a constant value across snapshots (ignoring renames), so
		// scripts really shouldn't have an ID. When changing this, keep it for the compareTo method
		// below, so we can keep consistent ordering with previous implementations.
		if (customBlockID != null) return customBlockID;
		if (blocks.size() == 0) return null;
		String id = "script:";
		for (Block block : blocks) {
			if (id.length() == 0) id += ",";
			id += block.getID();
		}
		return id;
	}

	@Override
	public int compareTo(Script other) {
		// For readability, put longer scripts first
		int lengthCompare = -Integer.compare(blocks.size(), other.blocks.size());
		if (lengthCompare != 0) return lengthCompare;
		// Break ties with IDs
		return getID().compareTo(other.getID());

	}
}
