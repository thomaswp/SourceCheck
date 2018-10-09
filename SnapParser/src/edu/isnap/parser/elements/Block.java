package edu.isnap.parser.elements;

import org.w3c.dom.Element;

public abstract class Block extends Code {
	private static final long serialVersionUID = 1L;

	public final String name;
	public final String id;

	@SuppressWarnings("unused")
	private Block() {
		this(null, null);
	}

	protected static String getID(Element element) {
		String id = element.getAttribute("id");
		if (id == null || id.length() == 0) return null;
		return id;
	}

	public Block(String name, String id) {
		this.name = name;
		this.id = id;
	}

	public String getID() {
		return id;
	}
}
