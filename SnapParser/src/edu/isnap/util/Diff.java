package edu.isnap.util;

import java.util.Arrays;
import java.util.List;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

// TODO: Extract to a Utils project and combine with SnapParser's Diff
public class Diff {
	public static boolean USE_ANSI_COLORS = true;

	public static String diff(String a, String b) {
		return diff(a, b, Integer.MAX_VALUE / 2);
	}

	public static String diff(String a, String b, int margin) {
		String[] original = a.split("\n");
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

			for (String deleted : delta.getOriginal().getLines()) {
				out += colorString("- " + deleted + "\n", 31);
			}
			for (String added : delta.getRevised().getLines()) {
				out += colorString("+ " + added + "\n", 32);
			}

			for (int i = chunkStart; i <= chunkEnd; i++) printed[i] = true;

			lastChunkEnd = chunkEnd;
		}

		if (lastChunkEnd >= 0) {
			int printStop = Math.min(lastChunkEnd + margin + 1, original.length - 1);
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

	private static String colorString(String string, int colorCode) {
		if (!USE_ANSI_COLORS) return string;
		return String.format("\u001b[%dm%s\u001b[0m", colorCode, string);
	}
}
