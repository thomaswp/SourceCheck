package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Stage extends Sprite {

	public final List<Sprite> sprites = new ArrayList<Sprite>();

	public Stage() {
		super("Stage");
	}
	
	public static Stage parse(Element element) {
		Stage stage = new Stage();
		Sprite.parseInto(element, stage);
		for (Code code : XML.getCodeInFirstChild(element, "sprites")) {
			stage.sprites.add((Sprite) code);
		}
		return stage;
	}
	
	@Override
	public String toCode() {
		return new CodeBuilder()
		.add("Stage")
		.indent()
		.add(sprites)
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(sprites);
		return "stage";
	}
}
