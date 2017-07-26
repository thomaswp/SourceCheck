package edu.isnap.parser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.dataset.Dataset;

public class CSVVerifier {

	public static void verify(Dataset dataset) throws IOException {
		verify(dataset.dataFile);
	}

	public static void verify(String path) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(path));

		int line = 0;
		int column = 0;
		int targetColumn = 0;
		boolean inQuote = false;
		boolean testingDouble = false;
		boolean error = false, brk = false;

		StringBuffer lastLine = null;
		StringBuffer currentLine = new StringBuffer();

		int cint;
		while ((cint = reader.read()) != -1) {
			char character = (char) cint;
			if (brk) break;
			if (character == '\r') continue;

			currentLine.append(character);

			if (inQuote) {
				if (character == '"') {
					testingDouble = !testingDouble;
					continue;
				} else {
					if (testingDouble) {
						inQuote = false;
						testingDouble = false;
						if (character != ',' && character != '\n') {
							error = true;
						}
					} else {
						continue;
					}
				}
			}

			switch (character) {
			case '\n':
				line++;
				if (targetColumn == 0) {
					targetColumn = column;
				} else  if (error || column != targetColumn) {
					error(line, column, inQuote, testingDouble, lastLine, currentLine,
							reader.readLine());
					brk = true;
					break;
				}

				if (line % 1000 == 0) System.out.print(currentLine);
				lastLine = currentLine;
				currentLine = new StringBuffer();
				column = 0;
				break;
			case ',':
				column++;
				break;
			case '"':
				inQuote = true;
				break;
			}
		}

		reader.close();
	}

	public static void verifyApache(String path, CSVFormat format) throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(path), format);
//		int columns = parser.getHeaderMap().size();
		int i = 0;
		for (CSVRecord record : parser) {
			if (i % 1000 == 0) {
				System.out.println(i + ": " + record.toString());
			}
			if (!record.isConsistent()) {
				System.out.println("Inconsistent record at row " + i);
				break;
			}
			i++;
		}
		parser.close();

	}

	private static void error(int line, int column, boolean inQuote, boolean testingDouble,
			StringBuffer lastLine, StringBuffer currentLine, String nextLine) {
		System.out.println("Line #: " + line);
		System.out.println("Columns: " + (column + 1));
		System.out.println("In quote: " + inQuote);
		System.out.println("Testing double quote: " + testingDouble);
		System.out.print("Last line:    " + lastLine);
		System.out.print("Current line: " + currentLine);
		System.out.print("Next line:    " + nextLine);
	}

	public static void main(String[] args) throws IOException {
		verifyApache("../data/bjc/bjc2017.csv", CSVFormat.DEFAULT.withEscape('\\').withHeader());
	}
}
