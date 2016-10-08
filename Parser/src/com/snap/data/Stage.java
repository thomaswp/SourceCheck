package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class Stage extends Sprite {
	private static final long serialVersionUID = 1L;

	public final List<Sprite> sprites = new ArrayList<Sprite>();

	@Override
	public String type() {
		return "stage";
	}

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
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add("Stage")
		.indent()
		.add(sprites)
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		ac.add(sprites);
	}
}
