package com.snap.data;

import org.w3c.dom.Element;

public abstract class Block extends Code implements IHasID {
	private static final long serialVersionUID = 1L;

	public final String name;
	public final int id;

	@SuppressWarnings("unused")
	private Block() {
		this(null, -1);
	}
	
	protected static int getID(Element element) {
		String idS = element.getAttribute("id");
		if (idS != null) {
			try {
				return Integer.parseInt(idS);
			}  catch (Exception e) { }
		}
		return -1;
	}
	
	public Block(String type, int id) {
		this.name = type;
		this.id = id;
	}
	
	@Override
	public Object getID() {
		return id;
	}
}
