import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.datasets.Fall2018;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

@SuppressWarnings("serial")
@WebServlet(name="dashboard", urlPatterns="/dashboard")

public class DashboardServlet extends HttpServlet {

	public static Assignment testData = Fall2018.PolygonMaker;

	public static List<AssignmentAttempt> selectAttemptsFromDatabase(
			Assignment assignment) throws Exception {
		SnapParser parser = new SnapParser(assignment, Mode.Ignore, true);
		String[] ids = null;
		String[] names = null;


		Map<String, AssignmentAttempt> attempts =
				parser.parseActionsFromDatabase(testData.name, ids, names);
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			selected.add(attempt);
		}
		return selected;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setContentType("text");
		resp.getOutputStream().println("Loaded for testing");

		List<AssignmentAttempt> attempts2;
		try {
			attempts2 = selectAttemptsFromDatabase(testData);
			System.out.println(attempts2.size());
			for (AssignmentAttempt attempt : attempts2) {

//				if (!attempt.id.equals("ba36c1cc-9e60-4c29-aef6-d07b20d11f6f")) continue;
				//BUG: ce5b3694-79f4-41ad-9712-3716e8b98877 cannot be found, since its assignmentID is none.
				// for each project (submission)
				if (attempt.size() == 0) continue;
				System.out.println(attempt.id);
				System.out.println(attempt.size());

//				for (AttemptAction action : attempt) {
//					System.out.println(action.message);
//					if (action.snapshot == null) continue;
//					Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
////					System.out.println(node.prettyPrint(true));
//				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String origin = req.getHeader("origin");
		if (origin != null) resp.setHeader("Access-Control-Allow-Origin", origin);
		resp.setHeader("Access-Control-Allow-Methods", "GET, POST");
		resp.setHeader("Access-Control-Allow-Headers",
				"Content-Type, Authorization, X-Requested-With");

		List<AssignmentAttempt> attempts2;
		try {
			attempts2 = selectAttemptsFromDatabase(testData);
			System.out.println(attempts2.size());
			for (AssignmentAttempt attempt : attempts2) {

//				if (!attempt.id.equals("ba36c1cc-9e60-4c29-aef6-d07b20d11f6f")) continue;
				//BUG: ce5b3694-79f4-41ad-9712-3716e8b98877 cannot be found, since its assignmentID is none.
				// for each project (submission)
				if (attempt.size() == 0) continue;
				System.out.println(attempt.id);
				System.out.println(attempt.size());

//				for (AttemptAction action : attempt) {
//					System.out.println(action.message);
//					if (action.snapshot == null) continue;
//					Node node = SimpleNodeBuilder.toTree(action.snapshot, true);
////					System.out.println(node.prettyPrint(true));
				PrintStream out = new PrintStream(resp.getOutputStream());

				String codeJSON = getCodeJSON();
				resp.setContentType("text/json");
				out.println(codeJSON);
//				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	private String getCodeJSON() {

		return null;
	}

}
