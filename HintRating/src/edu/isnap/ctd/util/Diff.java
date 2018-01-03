package edu.isnap.ctd.util;

import java.util.Arrays;
import java.util.List;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class Diff {
	public enum ColorStyle {
		None, ANSI, HTML
	}

	// To use ANSI colors in eclipse, get https://github.com/mihnita/ansi-econsole
	// Otherwise, for unicode, set the run configuration output for UTF-8
	public static ColorStyle colorStyle = ColorStyle.ANSI;

	public static String diff(String a, String b) {
		return diff(a, b, Integer.MAX_VALUE / 2);
	}

	public static String diff(String a, String b, int margin) {
		String[] original = a.split("\n");
		if (a.equals(b) && margin >= original.length) return a;
		Patch<String> diff = DiffUtils.diff(
				Arrays.asList(original), Arrays.asList(b.split("\n")));
		List<Delta<String>> deltas = diff.getDeltas();
		String out = "";
		int lastChunkEnd = -1;
		boolean[] printed = new boolean[original.length];
		for (Delta<String> delta : deltas) {
			Chunk<String> chunk = delta.getOriginal();
			int chunkStart = chunk.getPosition();
			int chunkEnd = chunkStart + chunk.getLines().size() - 1;
			if (lastChunkEnd >= 0) {
				int printStop = Math.min(lastChunkEnd + margin + 1, chunkStart - 1);
				for (int i = lastChunkEnd + 1; i <= printStop; i++) {
					out += "  " + original[i] + "\n";
					printed[i] = true;
				}
				if (printStop < chunkStart - 1) {
					out += "...\n";
				}
			}

			for (int i = Math.max(chunkStart - margin, 0); i < chunkStart; i++) {
				if (!printed[i]) {
					out += "  " + original[i] + "\n";
					printed[i] = true;
				}
			}

			List<String> originalLines = delta.getOriginal().getLines();
			List<String> revisedLines = delta.getRevised().getLines();
			if (colorStyle != ColorStyle.None &&  originalLines.size() == revisedLines.size()) {
				String splitRegex = "[^A-Za-z]";
				for (int i = 0; i < originalLines.size(); i++) {
					String deleted = originalLines.get(i);
					String added = revisedLines.get(i);
					String[] s1 = split(deleted.trim(), splitRegex);
					String[] s2 = split(added.trim(), splitRegex);
					if (Alignment.alignCost(s1, s2) < deleted.trim().length() * 0.5) {
						out += "~ ";
						for (int j = 0; j < added.length(); j++) {
							char c = added.charAt(j);
							if (Character.isWhitespace(c)) out += c;
							else break;
						}
						out += inlineDiff(s1, s2) + "\n";
					} else {
						out += colorString("- " + deleted, false) + "\n";
						out += colorString("+ " + added, true) + "\n";
					}
				}
			} else {
				for (String deleted : originalLines) {
					out += colorString("- " + deleted, false) + "\n";
				}
				for (String added : revisedLines) {
					out += colorString("+ " + added, true) + "\n";
				}
			}

			for (int i = chunkStart; i <= chunkEnd; i++) printed[i] = true;

			lastChunkEnd = chunkEnd;
		}

		if (lastChunkEnd >= 0) {
			int printStop = Math.min(lastChunkEnd + margin, original.length - 1);
			for (int i = lastChunkEnd + 1; i <= printStop; i++) {
				out += "  " + original[i] + "\n";
				printed[i] = true;
			}
			if (printStop < original.length - 1) {
				out += "...\n";
			}
		}
		return out;
	}

	private static String colorString(String string, boolean add) {
		if (colorStyle == ColorStyle.None) return string;
		return colorStringInline(string, add);
	}

	public static String inlineDiff(String a, String b, String splitRegex) {
		String[] aList = split(a, splitRegex), bList = split(b, splitRegex);
		return inlineDiff(aList, bList);
	}

	private static String inlineDiff(String[] aList, String[] bList) {
		List<String> original;
		Patch<String> diff = DiffUtils.diff(original = Arrays.asList(aList), Arrays.asList(bList));
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
		out.append(colorStringInline(last.toString(), lastAdd));
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

	private static String[] split(String string, String splitRegex) {
		return string.split(String.format("((?<=%1$s)|(?=%1$s))", splitRegex));
	}

	private static String colorStringInline(String string, boolean add) {
		if (colorStyle == ColorStyle.ANSI) {
			int colorCode = add ? 32 : 31;
			return String.format("\u001b[%dm%s\u001b[0m", colorCode, string);
		} else if (colorStyle == ColorStyle.HTML){
			return String.format("<span class=\"code-%s\">%s</span>",
					add ? "add" : "delete", string);
		} else {
			return String.format("%s%s%s", add ? "\u3008" : "\u300A", string,
					add ? "\u3009" : "\u300B");
		}
	}
}
