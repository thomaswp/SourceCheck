package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.List;

public class CodeBuilder {
	
	private String code = "";
	private int indent = 0;
	private boolean canon;
	private boolean ignore;
	
	public CodeBuilder(boolean canon) {
		this.canon = canon;
	}

	public CodeBuilder add(Code code) {
		if (ignore) return this;
		if (code == null) return this;
		addIndent();
		String content = code.toCode(canon);
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
		if (ignore) return this;
		addIndent();
		if (code.length() > 0 && !Character.isWhitespace(code.charAt(code.length() - 1))) {
			code += " ";
		}
		code += "{\n";
		indent++;
		return this;
	}
	
	public CodeBuilder close() {
		if (ignore) return this;
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

	public CodeBuilder add(String content, String canonical) {
		if (ignore) return this;
		if (content == null) return this;
		addIndent();
		code += canon ? canonical : content;
		return this;
	}
	
	public CodeBuilder add(String content) {
		return add(content, content);
	}

	public CodeBuilder addParameters(List<? extends Code> parameters) {
		if (ignore) return this;
		List<String> strings = new ArrayList<String>();
		for (Code c : parameters) strings.add(c.toCode(canon));
		return addSParameters(strings);
	}

	public CodeBuilder addSParameters(List<String> parameters) {
		if (ignore) return this;
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
		if (ignore) return this;
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
		if (ignore) return this;
		if (codes.size() == 0) return this;
		for (int i = 0; i < codes.size(); i++) {
			add(codes.get(i));
			if (nl) cnl();
			else if (i < codes.size() - 1) code += ", ";
		}
		return this;
	}
	
	public CodeBuilder cnl() {
		if (ignore) return this;
		if (code.endsWith("\n")) return this;
		return nl();
	}
	
	public CodeBuilder nl() {
		if (ignore) return this;
		code += "\n";
		return this;
	}

	public CodeBuilder ifNotCanon() {
		if (canon) ignore = true;
		return this;
	}
	
	public CodeBuilder endIf() {
		ignore = false;
		return this;
	}
}
