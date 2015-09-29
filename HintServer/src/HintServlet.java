import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
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
import com.snap.graph.subtree.SubtreeBuilder.HintComparator;


@SuppressWarnings("serial")
@WebServlet(name="hints", urlPatterns="/hints")
public class HintServlet extends HttpServlet {

	private static SubtreeBuilder builder;
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		loadBuilder();
		resp.setContentType("text");
		resp.getOutputStream().println("Loaded cache");
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
		
		resp.setContentType("text/html");
		out.println("<pre>");
//		out.println(snapshot.toCode());
		getHint(snapshot, out);
		out.println("</pre>");
		
//		resp.getOutputStream().println(builder.graph.size());
	}
	
	private void getHint(Snapshot snapshot, PrintStream out) {
		loadBuilder();
		Node node = Node.fromTree(null, SimpleTreeBuilder.toTree(snapshot, 0, true), true);
		
		List<Hint> hints = builder.getHints(node);
		Collections.sort(hints, HintComparator.ByContext.then(HintComparator.ByQuality));
		
		int context = Integer.MAX_VALUE;
		int printed = 0;
		for (int i = 0; i < hints.size() && printed < 10; i++) {
			Hint hint = hints.get(i);
			if (hint.context == context) {
				continue;
			}
			context = hint.context;
			printed++;
			out.println(hints.get(i));
		}
	}
	
	private void loadBuilder() {
		if (builder == null) {
			Kryo kryo = SubtreeBuilder.getKryo();
			InputStream stream = getServletContext().getResourceAsStream("WEB-INF/data/guess1Lab.cached");
			builder = kryo.readObject(new Input(stream), SubtreeBuilder.class); 
		}
	}
}