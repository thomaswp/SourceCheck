package edu.isnap.eval.python;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.util.Alignment;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.ListMap;

public class ProgressCalculator {

	private static String DATA_PATH = "../R/ITAP/data/DataChallenge/";

	public static void main(String[] args) throws FileNotFoundException, IOException {
		Map<String, String> codeMap = getCodeMap();
		CSVParser parser = new CSVParser(
				new FileReader(DATA_PATH + "MainTable.csv"), CSVFormat.DEFAULT.withHeader());


		ListMap<String, CSVRecord> problemMap = new ListMap<>();

		for (CSVRecord row : parser) {
			problemMap.add(row.get("ProblemID"), row);
		}
		parser.close();

		Spreadsheet spreadsheet = new Spreadsheet();

		for (String problemID : problemMap.keySet()) {
			parseProblem(problemID, problemMap.get(problemID), codeMap, spreadsheet);
		}
		spreadsheet.write(DATA_PATH + "Example/progress.csv");
	}

	static void parseProblem(String problem, List<CSVRecord> rows, Map<String, String> codeMap,
			Spreadsheet spreadsheet) {

		Set<String> solutions = new HashSet<>();
		ListMap<String, String> attemptMap = new ListMap<>();

		String currentUser = null;
		List<String> attempt = null;
		boolean correct = false;
		for (CSVRecord row : rows) {
			String user = row.get("SubjectID");
			if (user != currentUser) {
				if (currentUser != null && !correct) {
					attemptMap.put(currentUser, attempt);
				}

				currentUser = user;
				attempt = new ArrayList<>();
				correct = false;
			}

			boolean rowCorrect = row.get("Correct").equals("TRUE");
			String code = codeMap.get(row.get("CodeStateID"));
			attempt.add(code);

			if (rowCorrect) {
				correct = true;
				solutions.add(code);
			}
		}

		List<List<String>> solutionTokens = solutions.stream().map(ProgressCalculator::tokenize)
				.collect(Collectors.toList());

		System.out.println(problem);

		if (solutions.size() == 0) {
			// TODO: deal
			return;
		}


		for (String user : attemptMap.keySet() ) {
			attempt = attemptMap.get(user);
			// for now, just use last attempt
			String source = attempt.get(attempt.size() - 1);
			List<String> attemptTokens = tokenize(source);

			spreadsheet.newRow();
			spreadsheet.put("ProblemID", problem);
			spreadsheet.put("SubjectID", user);


			double bestProgress = findBestProgress(solutions, solutionTokens, source,
					attemptTokens);
			spreadsheet.put("BestProgress", bestProgress);

			source = attempt.get(0);
			attemptTokens = tokenize(source);
			double firstProgress = findBestProgress(solutions, solutionTokens, source,
					attemptTokens);
			spreadsheet.put("FirstProgress", firstProgress);
		}

		System.out.println("---------------------------------------------------");
		System.out.println();
	}

	private static double findBestProgress(Set<String> solutions,
			List<List<String>> solutionTokens, String source,
			List<String> attemptTokens) {
		double bestProgress = Double.NEGATIVE_INFINITY;
		int bestIndex = -1;
		for (int i = 0; i < solutionTokens.size(); i++) {
			List<String> solutionTokenList = solutionTokens.get(i);
			double progress = Alignment.getProgress(
					attemptTokens.toArray(new String[attemptTokens.size()]),
					solutionTokenList.toArray(new String[solutionTokenList.size()]),
					2, 1, 0.5);
			if (progress > bestProgress) {
				bestProgress = progress;
				bestIndex = i;
			}
		}

		bestProgress /= attemptTokens.size() * 2;

		System.out.println(source);
		System.out.println("Prog: " + bestProgress);
		System.out.println(solutions.toArray(new String[solutions.size()])[bestIndex]);
		System.out.println("-------");
		return bestProgress;
	}

	static List<String> tokenize(String source) {
		source = source.replaceAll("[\\s|\\n|\\r]+", " ");
		List<String> tokens = new ArrayList<>();
		String[] delimeters = new String[] {
				" ", "(", ")", "+", "-", ",", ":", "\\", "/", "*", "//", "/", "[", "]", "{", "}",
				"==", "=", ">", "<", "'", "\"", ".", "?", "!", "%", "^", "*"
		};
		String token = "";
		for (int i = 0; i < source.length(); i++) {
			boolean d = false;
			for (String delim : delimeters) {
				if (delim.length() + i >= source.length()) continue;
				if (source.substring(i).startsWith(delim)) {
					tokens.add(token);
					if (!delim.equals(" ")) tokens.add(delim);
					token = "";
					i += delim.length() - 1;
					d = true;
					break;
				}
			}
			if (!d) token += source.charAt(i);
		}
		if (!token.isEmpty()) tokens.add(token);
		return tokens;
	}

	static Map<String, String> getCodeMap() throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(
				new FileReader(DATA_PATH + "CodeStates/CodeState.csv"),
				CSVFormat.DEFAULT.withHeader());
		Map<String, String> codeMap = new HashMap<>();
		for (CSVRecord row : parser) {
			String id = row.get("CodeStateID");
			String code = row.get("Code");
			codeMap.put(id, code);
		}
		parser.close();
		return codeMap;
	}
}
