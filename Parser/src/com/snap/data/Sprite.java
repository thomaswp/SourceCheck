package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Sprite extends Code {
	public final String name;
	public final List<String> variables = new ArrayList<String>();
	public final List<Script> scripts = new ArrayList<Script>();
	public final List<BlockDefinition> blocks = new ArrayList<BlockDefinition>();
	
	public Sprite(String name) {
		this.name = name == null ? "Stage" : name;
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
	public String toCode() {
		return new CodeBuilder()
		.add(name)
		.indent()
		.add(variables.size() == 0 ? null : ("variables: " + variables.toString() + "\n"))
		.add(blocks)
		.add(scripts)
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(canonicalizeVariables(variables, canon));
		ac.add(scripts);
		ac.add(blocks);
		return name;
	}
}
