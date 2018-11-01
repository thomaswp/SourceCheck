import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import edu.isnap.ctd.hint.Hint;
import edu.isnap.ctd.hint.HintGenerator;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.hint.HintDebugInfo;
import edu.isnap.hint.HintMap;
import edu.isnap.hint.HintMapBuilder;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.unittest.UnitTest;


@SuppressWarnings("serial")
@WebServlet(name="hints", urlPatterns="/hints")
public class HintServlet extends HttpServlet {

	private final static String DEFAULT_ASSIGNMENT = "guess1Lab";
	private final static int DEFAULT_MIN_GRADE = 100;

	private static HashMap<String, HintMap> hintMaps = new HashMap<>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		loadHintMap(DEFAULT_ASSIGNMENT, null, DEFAULT_MIN_GRADE);
		resp.setContentType("text");
		resp.getOutputStream().println("Loaded cache for " + DEFAULT_ASSIGNMENT);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String origin = req.getHeader("origin");
		if (origin != null) resp.setHeader("Access-Control-Allow-Origin", origin);
		resp.setHeader("Access-Control-Allow-Methods", "GET, POST");
		resp.setHeader("Access-Control-Allow-Headers",
				"Content-Type, Authorization, X-Requested-With");

		Scanner sc = new Scanner(req.getInputStream());
		StringBuilder sb = new StringBuilder();
		while (sc.hasNextLine()) sb.append(sc.nextLine());
		sc.close();

		String xml = sb.toString();

		PrintStream out = new PrintStream(resp.getOutputStream());

		int minGrade = DEFAULT_MIN_GRADE;
		String mgs = req.getParameter("minGrade");
		if (mgs != null) {
			try {
				minGrade = Integer.parseInt(mgs);
			} catch (Exception e) { }
		}

		String hint = req.getParameter("hint");
		String assignment = req.getParameter("assignmentID");
		if (assignment == null){
			assignment = DEFAULT_ASSIGNMENT;
		}
		String dataset = req.getParameter("dataset");
		String hintTypes = req.getParameter("hintTypes");

		if (hint != null) {
			out.println(UnitTest.saveUnitTest(assignment, xml, hint));
		} else {
			String hintJSON = getHintJSON(xml, assignment, dataset, minGrade, hintTypes);
			resp.setContentType("text/json");
			out.println(hintJSON);
		}
	}

	private String getHintJSON(String snapshotXML, String assignment, String dataset, int minGrade,
			String hintTypes) {
		JSONArray array = new JSONArray();
		List<Hint> hints = new LinkedList<>();
		HintMap hintMap;
		Node node;

		try {
			Snapshot snapshot = Snapshot.parse(null, snapshotXML);
			hintMap = loadHintMap(assignment, dataset, minGrade);
			if (hintMap == null) {
				if ("view".equals(assignment)) return "[]";
				String message = "No hint map for assignment: " + assignment;
				JSONObject error = HintJSON.errorToJSON(new RuntimeException(message), false);
				return "[" + error + "]";
			}

			// Since the node is not being stored/logged, it doesn't matter if its IDs are
			// consistent across snapshots, so we use the CompleteDynamicIDer to make sure
			// every node has an ID for debugging purposes
			node = SimpleNodeBuilder.toTree(snapshot, true,
					new SimpleNodeBuilder.CompleteDynamicIDer());
		} catch (Exception e) {
			array.put(HintJSON.errorToJSON(e, true));
			return array.toString();
		}

		// Return the hints for each type requested, or bubble hints if none is provided
		if (hintTypes != null) hintTypes = hintTypes.toLowerCase();
		if (hintTypes == null || hintTypes.contains("bubble")) {
			try {
				hints.addAll(new HintGenerator(hintMap).getHints(node));
			} catch (Exception e) {
				array.put(HintJSON.errorToJSON(e, true));
			}
		}
		if (hintTypes != null && hintTypes.contains("highlight")){
			try {
				HintHighlighter highlighter = new HintHighlighter(hintMap);
				// TODO: Use an actual logging framework
//				highlighter.trace = System.out;

				if (hintTypes.contains("debug")) {
					HintDebugInfo info = highlighter.debugHighlight(node);
					hints.addAll(info.edits);
					array.put(info.toJSON());
				} else {
					hints.addAll(highlighter.highlightWithPriorities(node));
				}
			} catch (Exception e) {
				array.put(HintJSON.errorToJSON(e, true));
			}
		}

		for (Hint hint : hints) {
			array.put(HintJSON.hintToJSON(hint));
		}

		return array.toString();
	}

	private HintMap loadHintMap(String assignment, String dataset, int minGrade) {
		if (assignment == null || "test".equals(assignment)) {
			assignment = DEFAULT_ASSIGNMENT;
		}
		String key = assignment + dataset + minGrade;
		HintMap hintMap = hintMaps.get(key);
		if (hintMap == null) {
			Kryo kryo = SnapHintBuilder.getKryo();
			String path = String.format("/WEB-INF/data/%s-g%03d%s.cached", assignment, minGrade,
					dataset == null ? "" : ("-" + dataset));
			InputStream stream = getServletContext().getResourceAsStream(path);
			if (stream == null) return null;
			Input input = new Input(stream);
			HintMapBuilder builder = kryo.readObject(input, HintMapBuilder.class);
			input.close();

			hintMaps.put(key, hintMap = builder.hintMap);
		}
		return hintMap;
	}
}
