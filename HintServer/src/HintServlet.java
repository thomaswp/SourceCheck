import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.snap.graph.subtree.SubtreeBuilder;


@SuppressWarnings("serial")
@WebServlet(name="hints", urlPatterns="/hints")
public class HintServlet extends HttpServlet {

	private static SubtreeBuilder builder;
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		resp.setContentType("text");
		resp.getOutputStream().println("Hello, world");
		
		if (builder == null) {
			Kryo kryo = SubtreeBuilder.getKryo();
			InputStream stream = getServletContext().getResourceAsStream("WEB-INF/data/guess1Lab.cached");
//			InputStream stream = getServletContext().getResourceAsStream("/WEB-INF/classes/HintServlet.class");

			resp.getOutputStream().println(stream != null);
			builder = kryo.readObject(new Input(stream), SubtreeBuilder.class); 
		}
		resp.getOutputStream().println(builder.graph.size());
	}
}
