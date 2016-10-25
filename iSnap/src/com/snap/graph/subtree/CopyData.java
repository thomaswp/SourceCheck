package com.snap.graph.subtree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.snap.parser.Spring2016;

public class CopyData {
	public static void main(String[] args) throws IOException {
		copyGraphs(Spring2016.dataDir);
	}

	protected static void copyGraphs(String fromDir) throws IOException {
		String toDir = "../HintServer/WebContent/WEB-INF/data";
		for (String file : new File(fromDir).list()) {
			Path fromPath = new File(fromDir, file).toPath();
			Path toPath = new File(toDir, file).toPath();
			if (file.endsWith(".cached")) {
				Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}


}
