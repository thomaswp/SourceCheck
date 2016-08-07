import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Hint;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder;


@SuppressWarnings("serial")
@WebServlet(name="hints", urlPatterns="/hints")
public class HintServlet extends HttpServlet {

	private final static String DEFAULT_ASSIGNMENT = "guess1Lab";
	private final static int DEFAULT_MIN_GRADE = 100;

	private static HashMap<String, SubtreeBuilder> builders =
			new HashMap<String, SubtreeBuilder>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		loadBuilder(DEFAULT_ASSIGNMENT, DEFAULT_MIN_GRADE);
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
		Snapshot snapshot = Snapshot.parse(null, xml);

		PrintStream out = new PrintStream(resp.getOutputStream());

		int minGrade = DEFAULT_MIN_GRADE;
		String mgs = req.getParameter("minGrade");
		if (mgs != null) {
			try {
				minGrade = Integer.parseInt(mgs);
			} catch (Exception e) { }
		}

		resp.setContentType("text/json");
//		out.println(snapshot.toCode());
		getHint(snapshot, req.getParameter("assignmentID"), minGrade, out);

//		resp.getOutputStream().println(builder.graph.size());
	}

	private void getHint(Snapshot snapshot, String assignment, int minGrade,
			PrintStream out) {
		SubtreeBuilder builder = loadBuilder(assignment, minGrade);
		if (builder == null) {
			out.println("[]");
			return;
		}

//		long time = System.currentTimeMillis();

//		System.out.println(snapshot.toCode(true));
		Node node = SimpleNodeBuilder.toTree(snapshot, true);

		List<Hint> hints = builder.getHints(node);
//		Collections.sort(hints, HintComparator.ByContext.then(HintComparator.ByAlignment));

		out.println("[");
		for (int i = 0; i < hints.size(); i++) {
			if (i > 0) out.println(",");
			out.print(hintToJson(hints.get(i)));
		}
		out.println("]");

//		long elapsed = System.currentTimeMillis() - time;
//		System.out.println(elapsed);
	}

	public static String hintToJson(Hint hint) {
		return String.format("{\"from\": \"%s\", \"to\": \"%s\", \"data\": %s}",
				hint.from(), hint.to(), hint.data());
	}

	private SubtreeBuilder loadBuilder(String assignment, int minGrade) {
		if (assignment == null || "test".equals(assignment)) {
			assignment = DEFAULT_ASSIGNMENT;
		}
		SubtreeBuilder builder = builders.get(assignment);
		if (builder == null) {
			Kryo kryo = SubtreeBuilder.getKryo();
			InputStream stream = getServletContext().getResourceAsStream(
					String.format("/WEB-INF/data/%s-g%03d.cached", assignment, minGrade));
			if (stream == null) return null;
			Input input = new Input(stream);
			builder = kryo.readObject(input, SubtreeBuilder.class);
			input.close();

			builders.put(assignment, builder);
		}
		return builder;
	}
}
