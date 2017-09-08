package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.parser.elements.util.XML;

public class Sprite extends Code implements IHasID {
	private static final long serialVersionUID = 1L;

	public final String name;
	public final List<String> variables = new ArrayList<>();
	public final List<Script> scripts = new ArrayList<>();
	public final BlockDefinitionGroup blocks;

	@Override
	public String type() {
		return "sprite";
	}

	@Override
	public String value() {
		return name;
	}

	@SuppressWarnings("unused")
	private Sprite() {
		this(null);
	}

	public Sprite(String name) {
		this.name = name == null ? "Stage" : name;
		this.blocks = new BlockDefinitionGroup(getID());
	}

	public static Sprite parse(Element element) {
		Sprite sprite = new Sprite(element.getAttribute("name"));
		parseInto(element, sprite);
		return sprite;
	}

	protected static void parseInto(Element element, Sprite sprite) {
		for (Element variable : XML.getGrandchildrenByTagName(element, "variables", "variable")) {
			sprite.variables.add(variable.getAttribute("name"));
		}
		for (Code code : XML.getCodeInFirstChild(element, "scripts")) {
			sprite.scripts.add((Script) code);
		}
		for (Code code : XML.getCodeInFirstChild(element, "blocks")) {
			sprite.blocks.add((BlockDefinition) code);
		}
	}

	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add(name)
		.indent()
		.add(variables.size() == 0 ? null : ("variables: " + variables.toString() + "\n"))
		.add(blocks.getWithEdits(true))
		.add(scripts)
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		addVariables(ac, variables);
		ac.add(scripts);
		ac.add(blocks.getWithEdits(canon));
	}

	@Override
	public String getID() {
		return name;
	}
}
