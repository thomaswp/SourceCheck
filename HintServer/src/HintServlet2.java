import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import edu.isnap.ctd.hint.CTDHintGenerator;
import edu.isnap.ctd.hint.Hint;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.hint.HintData;
import edu.isnap.hint.HintDebugInfo;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.python.PythonNode;
import edu.isnap.python.SourceCodeHighlighter;
import edu.isnap.sourcecheck.HintHighlighter;

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
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//		super.doPut(req, resp);
		
		loadHintMap(DEFAULT_ASSIGNMENT, "", DEFAULT_MIN_GRADE);
		
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

			JSONObject parsedTree = new JSONObject(parsedTreeRaw);
			PythonNode fullStudentCode = PythonNode.fromJSON(parsedTree, originalSource);

			String highlightedCode = SourceCodeHighlighter.highlightSourceCode(hintDatas.get(problemName), fullStudentCode);
//			String jsonString = "{'output' : '" + highlightedCode + "'}";
			JSONObject jsonObj = new JSONObject();
			jsonObj.put("highlighted", highlightedCode);
			
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
