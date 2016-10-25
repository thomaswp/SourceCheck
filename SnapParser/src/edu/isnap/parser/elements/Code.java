package edu.isnap.parser.elements;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.isnap.parser.elements.util.Canonicalization;

public abstract class Code implements Serializable {
	private static final long serialVersionUID = 1L;

	public abstract String toCode(boolean canon);
	public abstract String type();
	public abstract String name(boolean canon);
	public abstract void addChildren(boolean canon, Accumulator ac);

	public String toCode() {
		return toCode(false);
	}

	public List<Block> getAllBlocks(final boolean canon) {
		final List<Block> blocks = new ArrayList<Block>();
		if (this instanceof Block) {
			blocks.add((Block) this);
		}
		addChildren(canon, new Accumulator() {
			@Override
			public void addVariables(List<String> codes) { }

			@Override
			public void add(Iterable<? extends Code> codes) {
				for (Code code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Code code) {
				blocks.addAll(code.getAllBlocks(canon));
			}

			@Override
			public void add(Canonicalization canon) { }
		});
		return blocks;
	}

	public List<Code> getAllCode(final boolean canon) {
		final List<Code> codes = new ArrayList<Code>();
		codes.add(this);
		addChildren(canon, new Accumulator() {
			@Override
			public void addVariables(List<String> codes) { }

			@Override
			public void add(Iterable<? extends Code> codes) {
				for (Code code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Code code) {
				codes.addAll(code.getAllCode(canon));
			}

			@Override
			public void add(Canonicalization canon) { }
		});
		return codes;
	}

	public interface Accumulator {
		void add(Code code);
		void add(Canonicalization canon);
		void add(Iterable<? extends Code> codes);
		void addVariables(List<String> vars);
//		void addParameters(List<Code>)
	}

	protected static List<String> canonicalizeVariables(List<String> variables, boolean canon) {
		if (!canon) return variables;
		List<String> vars = new ArrayList<String>();
		for (int i = 0; i < variables.size(); i++) vars.add("var");
		return vars;
	}

	protected static List<Block> list(Block... blocks) { return Arrays.asList(blocks); }
}
