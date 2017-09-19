package edu.isnap.ctd.util;

import java.util.Arrays;
import java.util.List;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class Diff {
	// To use ANSI colors in eclipse, get https://github.com/mihnita/ansi-econsole
	public static boolean USE_ANSI_COLORS = true;

	public static String ansiDiff(String a, String b, String splitRegex) {
		List<String> original;
		Patch<String> diff = DiffUtils.diff(
				original = split(a, splitRegex), split(b, splitRegex));
		List<Delta<String>> deltas = diff.getDeltas();
		StringBuilder sb = new StringBuilder();
		int lastPrinted = 0;
		for (Delta<String> delta : deltas) {
			Chunk<String> chunk = delta.getOriginal();
			for (; lastPrinted < chunk.getPosition(); lastPrinted++) {
				sb.append(original.get(lastPrinted));
			}
			for (String deleted : delta.getOriginal().getLines()) {
				sb.append(colorString(deleted, 31));
				lastPrinted++;
			}
			for (String added : delta.getRevised().getLines()) {
				sb.append(colorString(added, 32));
			}
		}
		for (; lastPrinted < original.size(); lastPrinted++) {
			sb.append(original.get(lastPrinted));
		}
		return sb.toString();
	}

	private static List<String> split(String string, String splitRegex) {
		return Arrays.asList(string.split(String.format("((?<=%1$s)|(?=%1$s))", splitRegex)));
	}

	private static String colorString(String string, int colorCode) {
		return String.format("\u001b[%dm%s\u001b[0m", colorCode, string);
	}

	public static void main(String[] args) {
		System.out.println(ansiDiff("[doSayFor]", "[doAsk, doSayFor]", "[\\[|\\]|,|\\s]"));
	}
}
