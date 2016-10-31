package edu.isnap.parser.elements.util;

import java.io.File;
import java.io.FileNotFoundException;

import edu.isnap.parser.elements.BlockDefinition;
import edu.isnap.parser.elements.Snapshot;

public class FindTools {
	public static void main(String[] args) throws FileNotFoundException {
		Snapshot snapshot = Snapshot.parse(new File("tools.xml"));
		System.out.println("public final static String[] TOOLS_BLOCKS = new String[] {");
		for (BlockDefinition block : snapshot.blocks.blocks) {
			System.out.println("\t\"" + block.name + "\",");
		}
		System.out.println("};");
		
		for (BlockDefinition block : snapshot.blocks.blocks) {
			if (!block.isImported) {
				System.out.println("Warning, tool block not ID'd: " + block.name);
			}
		}
	}
}
