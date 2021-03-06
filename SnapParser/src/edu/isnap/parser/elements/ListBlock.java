package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import edu.isnap.parser.elements.util.XML;

public class ListBlock extends Block {
	private static final long serialVersionUID = 1L;

	public final List<Block> list = new ArrayList<>();

	@Override
	public String type() {
		return "list";
	}

	@Override
	public String value() {
		return null;
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
