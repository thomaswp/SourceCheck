package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Sprite extends Code implements IHasID {
	private static final long serialVersionUID = 1L;

	public final String name;
	public final List<String> variables = new ArrayList<String>();
	public final List<Script> scripts = new ArrayList<Script>();
	public final BlockDefinitionGroup blocks;

	@Override
	public String name(boolean canon) {
		return canon ? type() : name;
	}

	@Override
	public String type() {
		return "sprite";
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
		.add(variables.size() == 0 ? null : ("variables: " + canonicalizeVariables(variables, canon).toString() + "\n"))
		.add(blocks.getWithEdits(canon))
		.add(scripts)
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		ac.addVariables(canonicalizeVariables(variables, canon));
		ac.add(scripts);
		ac.add(blocks.getWithEdits(canon));
	}

	@Override
	public String getID() {
		return name;
	}
}
