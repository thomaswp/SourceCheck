package edu.isnap.parser.elements;


public class VarBlock extends Block {
	private static final long serialVersionUID = 1L;

	@Override
	public String type() {
		return "var";
	}

	@Override
	public String value() {
		return name;
	}

	@SuppressWarnings("unused")
	private VarBlock() {
		this(null, null);
	}

	public VarBlock(String name, String id) {
		super(name, id);
	}

	@Override
	public String toCode(boolean canon) {
		return value();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) { }
}
