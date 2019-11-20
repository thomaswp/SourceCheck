import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapDatabaseParser;
import edu.isnap.parser.elements.Snapshot;
//import edu.isnap.eval.dashboard.VisualizationTest;


@SuppressWarnings("serial")
@WebServlet(name="dashboard", urlPatterns="/dashboard")


public class DashboardServlet extends HttpServlet {

	// TODO: Make configurable
	public final static String ASSIGNMENT_NAME = "squiralHW";

	public static List<AssignmentAttempt> selectAttemptsFromDatabase(String[] times)
			throws Exception {
		String[] ids = null;
		String[] names = null;
		//String[] times = {"2019-01-01"};

		Map<String, AssignmentAttempt> attempts =
				SnapDatabaseParser.parseActionsFromDatabaseWithTimestamps(ASSIGNMENT_NAME,
						ids, names, times);
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
			String[] times = {"2019-01-01"};
			attempts2 = selectAttemptsFromDatabase(times);
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

	/***
	 * this function takes a snapshot and returns the tree size of the resulting AST
	 * @param lastSnapshot is the last snapshot in a given attempt
	 * @return the size of the resulting abstract syntax tree
	 */
	public static int getTreeSize(Snapshot lastSnapshot) {
		Node node = SimpleNodeBuilder.toTree(lastSnapshot, true);
		return node.treeSize();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String origin = req.getHeader("origin");

		if (origin != null) resp.setHeader("Access-Control-Allow-Origin", origin);
		resp.setHeader("Access-Control-Allow-Methods", "GET, POST");
		resp.setHeader("Access-Control-Allow-Headers",
				"Content-Type, Authorization, X-Requested-With");

				PrintStream out = new PrintStream(resp.getOutputStream());

				String time = req.getParameter("time");
				System.out.println(time);
				List<AssignmentAttempt> attempts2;
				//jsonAttempts is a stack of the data we are getting from each attempt in JSON form
				Stack<String> jsonAttempts = new Stack<String>();
				//gets data from database and converts it into a JSON string
				try {
					String[] times = {time};
					attempts2 = selectAttemptsFromDatabase(times);
					System.out.println("attempt2 size " + attempts2.size());
					for(AssignmentAttempt attempt : attempts2) {

					    if (attempt.size() == 0) continue;
					    //gets treeSize of lastSnapshot
						Snapshot lastSnapshot = attempt.rows.getLast().lastSnapshot;
						int finalTreeSize = getTreeSize(lastSnapshot);
						//adds data to a string and pushes that string to jsonAttempts stack
						String JSONattempts = "{\"id\":"+"\""+attempt.id +"\""+",\"active\":"+attempt.totalActiveTime+
								",\"idle\":"+attempt.totalIdleTime+",\"size\":"+attempt.size()+
								",\"treeSize\":"+finalTreeSize+",\"assignment\":"+"\""+attempt.loggedAssignmentID+"\""+"}";
						jsonAttempts.push(JSONattempts);
					}
					System.out.println(jsonAttempts.size());
					//gets JSON String for resulting stack
					String codeJSON = getCodeJSON(jsonAttempts);
					resp.setContentType("text/json");
					out.println(codeJSON);


				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	}

	//returns a JSON string to return to the client side
	private String getCodeJSON(Stack<String> jsonStack) {
		System.out.println(jsonStack.size());
		return jsonStack.toString();
	}

}
