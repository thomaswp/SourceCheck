package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class ListBlock extends Block {
	private static final long serialVersionUID = 1L;

	public final List<Block> list = new ArrayList<Block>();

	@Override
	public String name(boolean canon) {
		return name;
	}

	@Override
	public String type() {
		return name;
	}

	public ListBlock() {
		super("list", null);
	}

	public static ListBlock parse(Element element) {
		ListBlock list = new ListBlock();
		for (Code code : XML.getCode(element)) {
			list.list.add((Block) code);
		}
		return list;
	}

	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add("[")
		.add(list, false)
		.add("]")
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		ac.add(list);
	}
}
