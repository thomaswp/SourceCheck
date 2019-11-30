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

			loadHintMap(problemName, "", DEFAULT_MIN_GRADE);

			JSONObject parsedTree = new JSONObject(parsedTreeRaw);
			TextualNode fullStudentCode = PythonNode.fromJSON(parsedTree, originalSource,
					PythonNode::new);

			String highlightedCode = SourceCodeHighlighter.highlightSourceCode(hintDatas.get(problemName), fullStudentCode);
			JSONObject jsonObj = new JSONObject();
			jsonObj.put("highlighted", highlightedCode);
			
			String html = buildHTML(highlightedCode);
			jsonObj.put("html", html);
			
			resp.setContentType("text/json");
			resp.getWriter().print(jsonObj);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Builds an HTML string from the annotated code with suggestions. For nicer display/easier integration into the PCRS system.
	 * The string will have a <div> block as the outermost element, with a <p>, <div>, and <p> as its children.
	 * The first paragraph is simply a display header. The highlighted code that is passed in will be split and used to make the
	 * second and third elements, which are the annotated student code and the suggestions for what to add, respectively.
	 * 
	 * @param highlightedCode The fully annotated code
	 * @return An HTML string contained in a <div> block
	 */
	private String buildHTML(String highlightedCode) {
		String header = "We have annotated your code below with some suggestions. Hover your mouse over them to see more details.";
		
		int split_index = highlightedCode.indexOf("You may be missing the following");
		String missing_summary = highlightedCode.substring(split_index);
		highlightedCode = highlightedCode.substring(0, split_index - 1);
		
		return "<div>" + 
				"<p>" + header + "</p>" +
				"<div class=display>" + highlightedCode + "</div>" +
				"<p class=missing>" + missing_summary + "</p>" +
//				"<iframe src=\"https://www.qualtrics.com\"></iframe>" +
			   "</div>";
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
