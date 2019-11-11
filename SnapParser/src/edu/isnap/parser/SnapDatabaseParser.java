package edu.isnap.parser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AssignmentAttempt.ActionRows;
import edu.isnap.parser.SnapParser.AttemptParams;
import edu.isnap.parser.SnapParser.RowBuilder;

public class SnapDatabaseParser {

	static String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static String DB_URL = "jdbc:mysql://localhost:3306/snap?useLegacyDatetimeCode=false&serverTimezone=EST5EDT";

	static String USER = "snap";
	static String PASS = "password";

	public static Map<String, AssignmentAttempt> parseActionsFromDatabase(String assignmentID, String[] ids, String[] names) throws Exception {

		Map<String, AssignmentAttempt> map = new HashMap<String, AssignmentAttempt>();
		Connection conn = null;
		Statement stmt = null;
		try {

			Class.forName("com.mysql.cj.jdbc.Driver");

			conn = DriverManager.getConnection(DB_URL, USER, PASS);
			stmt = conn.createStatement();

			PreparedStatement stment = conn
					.prepareStatement("SELECT DISTINCT projectID FROM trace WHERE assignmentID = ?");
			stment.setString(1, assignmentID);
			ResultSet result = stment.executeQuery();
			while (result.next()) {
				String projectID = result.getString("projectID");

				if (projectID.equals("")) {
					continue;
				}

				PreparedStatement statement = conn.prepareStatement("SELECT * FROM trace WHERE projectID = ?");
				statement.setString(1, projectID);
				ResultSet rs = statement.executeQuery();
				RowBuilder builder = new RowBuilder(projectID);
				while (rs.next()) {
					String action = rs.getString("message");
					int id = rs.getInt("id");
					String time = rs.getString("time");
					String data = rs.getString("data");

					String userID = rs.getString("userID");
					String session = rs.getString("sessionId");
					String xml = rs.getString("code");


					builder.addRow(action, data, userID, session, xml, id, time);

				}
				ActionRows rows = builder.finish();
				AttemptParams params = new AttemptParams(
						projectID, "", assignmentID, true, false, false);
				AssignmentAttempt attempt = SnapParser.parseRows(params, rows, null);

				map.put(projectID, attempt);
				//System.out.println("Parsed: " + projectID);
				//rs.close();
			}

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		} finally {
			// finally block used to close resources
			try {
				if (stmt != null) {
					conn.close();
				}
			} catch (SQLException se) {
			} // do nothing
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException se) {
				se.printStackTrace();
			} // end finally try
		} // end try

		return map;

	}


	/**
	 * parseActionsFromDatabaseWithTimestamps parses through the data given in the database between
	 * a specified start and end time and returns a map of the resulting rows
	 * @param assignmentID is the ID of the assignment that will be parsed
	 * @param ids
	 * @param names
	 * @param times is an array containing the start and end times
	 * @return a map containing all of the rows of information as a result of the given constraints
	 * @throws Exception
	 */
	public static Map<String, AssignmentAttempt> parseActionsFromDatabaseWithTimestamps(String assignmentID, String[] ids, String[] names, String[] times) throws Exception {

		Map<String, AssignmentAttempt> map = new HashMap<String, AssignmentAttempt>();
		Connection conn = null;
		Statement stmt = null;
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			Stack<String> users = new Stack<String>();
			conn = DriverManager.getConnection(DB_URL, USER, PASS);
			stmt = conn.createStatement();
			//gets projectIDs from database where the assignmentID = the given assignmentID
			PreparedStatement projectIdsStatement = conn
					.prepareStatement("SELECT DISTINCT projectID FROM trace WHERE assignmentID = ?");
			projectIdsStatement.setString(1, assignmentID);
			ResultSet projectIdsResults = projectIdsStatement.executeQuery();
			int count = 0;
			while (projectIdsResults.next()) {
				String projectID = projectIdsResults.getString("projectID");

				if (projectID.equals("")) {
					continue;
				}

				PreparedStatement traceDataStatement = conn.prepareStatement(
						"SELECT * FROM trace WHERE projectID = ? AND time < ?");

				traceDataStatement.setString(1, projectID);
				traceDataStatement.setString(2, times[0]);

				ResultSet traceData = traceDataStatement.executeQuery();
				RowBuilder builder = new RowBuilder(projectID);

				//adds resulting rows to builder
				while (traceData.next()) {
					String action = traceData.getString("message");
					int id = traceData.getInt("id");
					String time = traceData.getString("time");
					String data = traceData.getString("data");

					String userID = traceData.getString("userID");
					String session = traceData.getString("sessionId");
					String xml = traceData.getString("code");
					if(!(users.contains(userID))) {
						users.push(userID);
					}
					count++;
					//System.out.println(time);
					builder.addRow(action, data, userID, session, xml, id, time);

				}

				ActionRows rows = builder.finish();

				AttemptParams params = new AttemptParams(
						projectID, "", assignmentID, true, false, false);
				AssignmentAttempt attempt = SnapParser.parseRows(params, rows, null);

				//maps resulting information
				map.put(projectID, attempt);
				//				System.out.println("Parsed: " + projectID);
				traceData.close();

			}
			System.out.println("count: " + count);
			System.out.println(users.size());
		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		} finally {
			// finally block used to close resources
			try {
				if (stmt != null) {
					conn.close();
				}
			} catch (SQLException se) {
			} // do nothing
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException se) {
				se.printStackTrace();
			} // end finally try
		} // end try

		return map;

	}
}
