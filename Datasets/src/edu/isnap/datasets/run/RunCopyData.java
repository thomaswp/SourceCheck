package edu.isnap.datasets.run;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import edu.isnap.datasets.Spring2016;

public class RunCopyData {
	public static void main(String[] args) throws IOException {
		copyHintDatabaseToServer(Spring2016.dataDir);
	}

	protected static void copyHintDatabaseToServer(String fromDir) throws IOException {
		copyHintDatabaseToServer(fromDir, "");
	}

	protected static void copyHintDatabaseToServer(String fromDir, String datasetSuffix)
			throws IOException {
		if (datasetSuffix.length() > 0) datasetSuffix = "-" + datasetSuffix;
		String toDir = "../HintServer/WebContent/WEB-INF/data";
		String ext = ".hdata";
		for (String file : new File(fromDir).list()) {
			if (file.endsWith(ext)) {
				Path fromPath = new File(fromDir, file).toPath();
				String toName = file.replace(ext, datasetSuffix + ext);
				Path toPath = new File(toDir, toName).toPath();
				Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}


}
