package com.snap.data;

import java.util.ArrayList;
import java.util.List;

public class CodeBuilder {
	
	private String code = "";
	private int indent = 0;
	
	public CodeBuilder add(Code code) {
		addIndent();
		String content = code.toCode();
		if (content.endsWith("\n")) {
			content = content.trim();
			content = content.replace("\n", "\n" + i());
			content += "\n";
		} else {
			content = content.replace("\n", "\n" + i());
		}
		this.code += content;
		return this;
	}
	
	private void addIndent() {
		if (code.endsWith("\n")) code += i();
	}
	
	public CodeBuilder indent() {
		addIndent();
		if (code.length() > 0 && !Character.isWhitespace(code.charAt(code.length() - 1))) {
			code += " ";
		}
		code += "{\n";
		indent++;
		return this;
	}
	
	public CodeBuilder close() {
		indent--;
		addIndent();
		code += "}";
		nl();
		return this;
	}
	
	public String end() {
		while (indent > 0) {
			close();
		}
		return code;
	}
	
	private String i() {
		String indent = "";
		for (int i = 0; i < this.indent; i++) indent += "  ";
		return indent;
	}

	public CodeBuilder add(String content) {
		if (content == null) return this;
		addIndent();
		code += content;
		return this;
	}

	public CodeBuilder addParameters(List<? extends Code> parameters) {
		List<String> strings = new ArrayList<String>();
		for (Code c : parameters) strings.add(c.toCode());
		return addSParameters(strings);
	}

	public CodeBuilder addSParameters(List<String> parameters) {
		addIndent();
		code += "(";
		boolean comma = false;
		for (String c : parameters) {
			if (comma) code += ", ";
			code += c;
			comma = true;
		}
		code += ")";
		return this;
	}

	public CodeBuilder addBodies(List<? extends Code> bodies) {
		if (bodies.size() == 0) return this;
		for (Code body : bodies) {
			indent();
			add(body);
			close();
		}
		return this;
	}
	
	public CodeBuilder add(List<? extends Code> codes) {
		return add(codes, true);
	}
	
	public CodeBuilder add(List<? extends Code> codes, boolean nl) {
		if (codes.size() == 0) return this;
		for (Code code : codes) {
			add(code);
			if (nl) cnl();
		}
		return this;
	}
	
	public CodeBuilder cnl() {
		if (code.endsWith("\n")) return this;
		return nl();
	}
	
	public CodeBuilder nl() {
		code += "\n";
		return this;
	}
}
