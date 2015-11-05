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
import com.snap.graph.SimpleTreeBuilder;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.graph.subtree.SubtreeBuilder.Hint;


@SuppressWarnings("serial")
@WebServlet(name="hints", urlPatterns="/hints")
public class HintServlet extends HttpServlet {

	private final static String DEFAULT_ASSIGNMENT = "guess1Lab";
	
	private static HashMap<String, SubtreeBuilder> builders = 
			new HashMap<String, SubtreeBuilder>();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		loadBuilder(null);
		resp.setContentType("text");
		resp.getOutputStream().println("Loaded cache for " + DEFAULT_ASSIGNMENT);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String origin = req.getHeader("origin");
		if (origin != null) resp.setHeader("Access-Control-Allow-Origin", origin);
		resp.setHeader("Access-Control-Allow-Methods", "GET, POST");
		resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
		
		Scanner sc = new Scanner(req.getInputStream());
		StringBuilder sb = new StringBuilder();
		while (sc.hasNextLine()) sb.append(sc.nextLine());
		sc.close();
		
		String xml = sb.toString();
		Snapshot snapshot = Snapshot.parse(null, xml);
		
		PrintStream out = new PrintStream(resp.getOutputStream());
				
		resp.setContentType("text/json");
//		out.println(snapshot.toCode());
		getHint(snapshot, req.getParameter("assignmentID"), out);
		
//		resp.getOutputStream().println(builder.graph.size());
	}
	
	private void getHint(Snapshot snapshot, String assignment, PrintStream out) {
		SubtreeBuilder builder = loadBuilder(assignment);
		if (builder == null) {
			out.println("[]");
			return;
		}
		
		Node node = Node.fromTree(null, SimpleTreeBuilder.toTree(snapshot, 0, true), true);
		
		List<Hint> hints = builder.getHints(node);
//		Collections.sort(hints, HintComparator.ByContext.then(HintComparator.ByAlignment));
		
		out.println("[");
		for (int i = 0; i < hints.size(); i++) {
			if (i > 0) out.println(",");
			out.print(SubtreeBuilder.hintToJson(hints.get(i)));
		}
		out.println("]");
	}
	
	private SubtreeBuilder loadBuilder(String assignment) {
		if (assignment == null || "test".equals(assignment)) assignment = DEFAULT_ASSIGNMENT;
		SubtreeBuilder builder = builders.get(assignment);
		if (builder == null) {
			Kryo kryo = SubtreeBuilder.getKryo();
			InputStream stream = getServletContext().getResourceAsStream("/WEB-INF/data/" + assignment + ".cached");
			if (stream == null) return null;
			Input input = new Input(stream);
			builder = kryo.readObject(input, SubtreeBuilder.class);
			input.close();
			
			builders.put(assignment, builder);
		}
		return builder;
	}
}
