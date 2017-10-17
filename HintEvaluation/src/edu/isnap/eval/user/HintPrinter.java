package edu.isnap.eval.user;

import java.util.HashSet;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Diff;

public class HintPrinter {

	public final static HintPrinter Fall2016DeclinedFirstHints = new HintPrinter(
			"cbda4526-377a-4541-a2f7-cc3914af8907,1081501098 "
					+ "d166e87f-a82a-4e0c-b5fe-0d4c0483efc6,-1951526465 "
					+ "6655648d-edfd-4445-9a71-19078115edf3,-1078585468 "
					+ "6af85a7a-e708-449e-82b6-4d6d08bf5664,-329507030 "
					+ "2f9bb599-3551-4d27-8a2a-f95062a9ec00,133899552 "
					+ "7c4d8b94-36e3-466f-a5d0-1d69ded2c040,1785529237 "
					+ "86879778-90cc-4d4e-8648-52064cb8aa3e,390526193 "
					+ "b15bace5-74b5-4b0e-9592-e541c47b8678,905447092 "
					+ "0caddeec-8745-4996-a775-b85d6f9da818,2091153956 "
					+ "423d2f55-ff4c-445f-8d78-729abadd0abd,-1056504146 "
					+ "5273ed64-1463-47f4-b2f0-5e54edd39819,-1450820717 "
					+ "ad83bc71-54e8-481f-9f6e-43cd0fa75147,-589592238 "
					+ "d5b8c11c-a4cb-49ae-bcaf-2b92e9e7c330,-589592238 "
					+ "dd7e1cd5-c889-4cdf-a54d-e9eb35c3b197,882508789 "
					+ "e4aaeb62-cc56-429d-a2d8-d853fcc8251d,-912537432");

	public final static HintPrinter Fall2016GoodHints = new HintPrinter(
			"-2067670881 -1981336369 -1978715373 -1775036245 -1739158562 "
			+ "-1612579226 -1567509715 -1456381675 -1449585903 -1423735170 -1416861731 "
			+ "-1332981086 -1253329423 -1087282877 -1078969411 -1056504146 -1053381595 "
			+ "-997044331 -838052736 -730101922 -673679512 -589592238 -417506400 -238195422 "
			+ "-155678727 65971487 89028788 102719781 301656650 437408707 487951151 561099148 "
			+ "576774499 1260554602 1391251375 1413898133 1526049501 1771186917 1930303904 "
			+ "2037073311 2063224515");

	public final static HintPrinter Fall2016CommonHints = new HintPrinter(
			"-2067670881 -1978715373 -1951526465 -1908453461 -1775036245 -1739158562 -1567509715 "
			+ "-1456381675 -1423735170 -1253329423 -1248215080 -1087282877 -1085536704 -1078969411 "
			+ "-1056504146 -1053381595 -838052736 -673679512 -590008501 -589592238 -155678727 "
			+ "65971487 102719781 273780474 374246282 487951151 547834027 561099148 570317418 "
			+ "576774499 795750179 814548525 1260554602 1357843771 1526049501 1645369697 "
			+ "1771186917 1827078639 1904774912 1930303904 2037073311");

	private final HashSet<String> hashes = new HashSet<>();
	private final boolean hasUsers;

	public HintPrinter(String input) {
		for (String hash : input.split(" ")) {
			hashes.add(hash);
		}
		hasUsers = input.contains(",");
	}

	public void maybePrintHint(String attemptID, int hintHash, String assignment, Node node, Node hintOutcome) {
		String key = String.valueOf(hintHash);
		if (hasUsers) {
			key = attemptID + "," + key;
		}
		if (hashes.contains(key)) {
			hashes.remove(key);
			System.out.printf("%s %s %d\n%s\n", assignment, attemptID,
					hintHash, hintToString(node, hintOutcome));
		}
	}

	public static String hintToString(Node node, Node hintOutcome) {
		String original = node.prettyPrint();
		String outcome = hintOutcome.root().prettyPrint();
		return Diff.diff(original, outcome);
	}
}
