package edu.isnap.parser.elements;

import java.util.Arrays;
import java.util.HashSet;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import edu.isnap.parser.elements.util.XML;

public class LiteralBlock extends Block {
	private static final long serialVersionUID = 1L;

	public enum Type {
		Text(null), VarMenu(null), Option("option"), Color("color");

		public final String tag;

		Type(String tag) {
			this.tag = tag;
		}
	}

	public final String value;
	public final Type type;

	private final static HashSet<String> setVarBlocks = new HashSet<>(Arrays.asList(new String[] {
		"doSetVar",
		"doChangeVar",
		"doShowVar",
		"doHideVar"
	}));
	private final static HashSet<String> setVarInListBlocks =
			new HashSet<>(Arrays.asList(new String[] {
		"doDeclareVariables",
	}));

	@Override
	public String type() {
		return type == Type.VarMenu ? "varMenu" : "literal";
	}

	@Override
	public String value() {
		return value;
	}

	@SuppressWarnings("unused")
	private LiteralBlock() {
		this(null, null, null, null);
	}

	public LiteralBlock(String name, String id, Type type, String value) {
		super(name, id);
		this.type = type;
		this.value = value;
	}

	public static LiteralBlock parse(Element element) {
		Element parent = (Element) element.getParentNode();
		Type type = Type.Text;
		String value = element.getTextContent();
		if (parent != null) {
			if (XML.getFirstChild(parent) == element) {
				if (setVarBlocks.contains(parent.getAttribute("s"))) type = Type.VarMenu;
			}
			if ("list".equals(parent.getTagName())) {
				Node grandparent = parent.getParentNode();
				if (grandparent != null && grandparent instanceof Element) {
					if (XML.getFirstChild((Element) grandparent) == parent) {
						if (setVarInListBlocks.contains(
								((Element) grandparent).getAttribute("s"))) {
							type = Type.VarMenu;
						}
					}
				}
			}
		}
		if (type != Type.VarMenu) {
			Element child = XML.getFirstChild(element);
			if (child != null) {
				String tag = child.getTagName();
				for (Type t : Type.values()) {
					if (t.tag == null || !t.tag.equals(tag)) continue;
					type = t;
					value = child.getTextContent();
					break;
				}
			}
		}
		return new LiteralBlock(
				element.getTagName(),
				getID(element),
				type, value);
	}

	@Override
	public String toCode(boolean canon) {
		return value();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) { }
}
