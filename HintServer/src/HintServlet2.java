import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import edu.isnap.hint.HintData;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.node.TextualNode;
import edu.isnap.python.PythonNode;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.python.SourceCodeHighlighter.SourceCodeFeedbackHTML;
import edu.isnap.python.SourceCodeHighlighter.SourceCodeHighlightConfig;

@SuppressWarnings("serial")
@WebServlet(name="hints2", urlPatterns="/hints2")
public class HintServlet2 extends HttpServlet {

	private final static String DEFAULT_ASSIGNMENT = "firstAndLast";
	private final static double DEFAULT_MIN_GRADE = -1;

	private static HashMap<String, HintData> hintDatas = new HashMap<>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		loadHintMap(DEFAULT_ASSIGNMENT, "", DEFAULT_MIN_GRADE);
		resp.setContentType("text");
		resp.getOutputStream().println("HintServlet2 Loaded cache for " + DEFAULT_ASSIGNMENT);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPut(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//		super.doPut(req, resp);

		resp.setCharacterEncoding("UTF-8");
		String origin = req.getHeader("origin");
		if (origin != null) resp.setHeader("Access-Control-Allow-Origin", origin);
		resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT");
		resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");

		String requestData = req.getReader().lines().collect(Collectors.joining());

		try {
			JSONObject jsonAST = new JSONObject(requestData);
			String parsedTreeRaw = (String) jsonAST.get("parsed");
			String originalSource = (String) jsonAST.get("source");
			String problemName = (String) jsonAST.get("problem");
			String conditionSeed = (String) jsonAST.opt("condition_seed");

			loadHintMap(problemName, "", DEFAULT_MIN_GRADE);

			JSONObject parsedTree = new JSONObject(parsedTreeRaw);
			TextualNode fullStudentCode = PythonNode.fromJSON(parsedTree, originalSource,
					PythonNode::new);

			// TODO: This should be configured somewhere else for sure...
			// Reverse the conditions for these problems
			boolean reverseConditions = "69".equals(problemName) || "33".equals(problemName);

			SourceCodeHighlighter highlighter = new SourceCodeHighlighter(
					new SourceCodeHighlightConfig(conditionSeed, reverseConditions));
			SourceCodeFeedbackHTML feedback = highlighter.highlightSourceCode(
					hintDatas.get(problemName), fullStudentCode);
			JSONObject jsonObj = feedback.toJSON();

			// Eventually we should change this to HTML, but we'll keep it as highlighted for
			// backwards compatibility
//			String html = buildHTML(highlightedCode);
//			jsonObj.put("html", html);

			resp.setContentType("text/json");
			resp.getWriter().print(jsonObj);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private HintData loadHintMap(String assignment, String dataset, double minGrade) {
		if (assignment == null || "test".equals(assignment)) {
			assignment = DEFAULT_ASSIGNMENT;
		}

		String key = "";
		if(minGrade > 0) {
			key = assignment + dataset + minGrade;
		} else {
			key = assignment;
		}
		HintData hintData = hintDatas.get(key);
		if (hintData == null) {
			Kryo kryo = SnapHintBuilder.getKryo();
			String path = "/WEB-INF/data/";
			if(dataset == null) {
				path += SnapHintBuilder.getStorePath(assignment, minGrade, dataset);
			} else {
				path += assignment + ".hdata";
			}
			InputStream stream = getServletContext().getResourceAsStream(path);
			if (stream == null) return null;
			Input input = new Input(stream);
			try {
				hintData = kryo.readObject(input, HintData.class);
			} catch (Exception e) {
				e.printStackTrace();
			}
			input.close();

			hintDatas.put(key, hintData);
		}
		return hintData;
	}
}
