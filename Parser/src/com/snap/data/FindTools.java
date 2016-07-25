package com.snap.data;

import java.io.File;
import java.io.FileNotFoundException;

public class FindTools {
	public static void main(String[] args) throws FileNotFoundException {
		Snapshot snapshot = Snapshot.parse(new File("tools.xml"));
		System.out.println("public final static String[] TOOLS_BLOCKS = new String[] {");
		for (BlockDefinition block : snapshot.blocks.blocks) {
			System.out.println("\t\"" + block.name + "\",");
		}
		System.out.println("};");
		
		for (BlockDefinition block : snapshot.blocks.blocks) {
			if (!block.isToolsBlock) {
				System.out.println("Warning, tool block not ID'd: " + block.name);
			}
		}
	}
}
