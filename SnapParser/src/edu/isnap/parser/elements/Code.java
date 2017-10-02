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
	public abstract String value();
	public abstract void addChildren(boolean canon, Accumulator ac);

	public final String toCode() {
		return toCode(false);
	}

	// CallBlock has to handle this differently, so allow for overriding
	public String type(boolean canon) {
		return type();
	}

	public List<Block> getAllBlocks(final boolean canon) {
		final List<Block> blocks = new ArrayList<>();
		if (this instanceof Block) {
			blocks.add((Block) this);
		}
		addChildren(canon, new Accumulator() {
			@Override
			public void add(String type, String value) { }

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
		final List<Code> codes = new ArrayList<>();
		codes.add(this);
		addChildren(canon, new Accumulator() {
			@Override
			public void add(String type, String value) { }

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
		void add(String type, String value);
	}

	protected static void addVariables(Accumulator acc, List<String> variables) {
		for (String variable : variables) acc.add("varDec", variable);
	}

	protected static List<Block> list(Block... blocks) { return Arrays.asList(blocks); }
}
