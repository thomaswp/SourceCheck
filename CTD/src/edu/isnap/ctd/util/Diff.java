package edu.isnap.ctd.util;

import java.util.Arrays;
import java.util.List;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class Diff {
	// To use ANSI colors in eclipse, get https://github.com/mihnita/ansi-econsole
	// Otherwise, for unicode, set the run configuration output for UTF-8
	public static boolean USE_ANSI_COLORS = true;

	public static String inlineDiff(String a, String b, String splitRegex) {
		List<String> original;
		Patch<String> diff = DiffUtils.diff(
				original = split(a, splitRegex), split(b, splitRegex));
		List<Delta<String>> deltas = diff.getDeltas();
		StringBuilder sb = new StringBuilder();
		StringBuilder last = new StringBuilder();
		boolean add = true;
		int lastPrinted = 0;
		for (Delta<String> delta : deltas) {
			Chunk<String> chunk = delta.getOriginal();
			for (; lastPrinted < chunk.getPosition(); lastPrinted++) {
				flushColored(sb, last, add);
				sb.append(original.get(lastPrinted));
			}
			for (String deleted : delta.getOriginal().getLines()) {
				add = addColor(sb, last, deleted, false, add);
				lastPrinted++;
			}
			for (String added : delta.getRevised().getLines()) {
				add = addColor(sb, last, added, true, add);
			}
		}
		flushColored(sb, last, add);
		for (; lastPrinted < original.size(); lastPrinted++) {
			sb.append(original.get(lastPrinted));
		}
		return sb.toString();
	}

	private static void flushColored(StringBuilder out, StringBuilder last, boolean lastAdd) {
		if (last.length() == 0) return;
		out.append(colorString(last.toString(), lastAdd));
		last.setLength(0);
	}

	private static boolean addColor(StringBuilder out, StringBuilder last, String string,
			boolean add, boolean lastAdd) {
		if (add != lastAdd) {
			flushColored(out, last, lastAdd);
		}
		last.append(string);
		return add;
	}

	private static List<String> split(String string, String splitRegex) {
		return Arrays.asList(string.split(String.format("((?<=%1$s)|(?=%1$s))", splitRegex)));
	}

	private static String colorString(String string, boolean add) {
		if (USE_ANSI_COLORS) {
			int colorCode = add ? 32 : 31;
			return String.format("\u001b[%dm%s\u001b[0m", colorCode, string);
		} else {
			return String.format("%s%s%s", add ? "\u3008" : "\u300A", string,
					add ? "\u3009" : "\u300B");
		}
	}

	public static void main(String[] args) {
		System.out.println(inlineDiff("[doSayFor, abc]", "[doAsk, doSayFor]", "[\\[|\\]|,|\\s]"));
	}
}
