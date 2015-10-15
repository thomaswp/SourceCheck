package com.snap.graph.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.snap.graph.subtree.SubtreeBuilder.Hint;

public class MySQLHintMap implements HintMap {

	private final Connection con;
	private final String nodeTable, edgeTable;

	@Override
	public void clear() {
		try {			
			PreparedStatement dropNodes = con.prepareStatement(
					"DROP TABLE IF EXISTS " + nodeTable);
			
			PreparedStatement createNodes = con.prepareStatement(
					"CREATE TABLE IF NOT EXISTS " + nodeTable + " ( " +
						  "`id` int(11) NOT NULL AUTO_INCREMENT, " +
						  "`hash` int(32) NOT NULL, " + 
						  "`type` varchar(64) NOT NULL, " + 
						  "`value` text NOT NULL, " + 
						  "PRIMARY KEY(`id`)" +
						")");
			
			PreparedStatement dropEdges = con.prepareStatement(
					"DROP TABLE IF EXISTS " + edgeTable);
			
			PreparedStatement createEdges = con.prepareStatement(
					"CREATE TABLE IF NOT EXISTS " + edgeTable + " ( " +
						  "`id` int(11) NOT NULL AUTO_INCREMENT, " +
						  "`fromID` int(11) NOT NULL, " + 
						  "`toID` int(11) NOT NULL, " + 
						  "`weight` int(11) NOT NULL, " +
						  "PRIMARY KEY(`id`)" +
						")");
			
			con.setAutoCommit(false);
			
			dropNodes.execute();
			createNodes.execute();
			con.prepareStatement("CREATE INDEX `hashIndex` ON " + nodeTable + " (`hash`)").execute();
			dropEdges.execute();
			createEdges.execute();
			con.prepareStatement("CREATE INDEX `fromIndex` ON " + edgeTable + " (`fromID`)").execute();
			con.prepareStatement("ALTER TABLE " + edgeTable + " ADD UNIQUE INDEX(fromID, toID)").execute();
			
			
			con.commit();
			con.setAutoCommit(true);
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public MySQLHintMap(Connection con, String tablePrefix) {
		this.con = con;
		nodeTable = tablePrefix + "_nodes";
		edgeTable = tablePrefix + "_edges";
	}
	
	public void addVertex(Node node) {
		getVertexIDAndInsert(node);
	}
	
	private int getVertexIDAndInsert(Node node) {
		Integer id = getVertexID(node);
		if (id != null) return id;
		try {
			PreparedStatement insert = con.prepareStatement(
					"INSERT INTO " + nodeTable + " (`hash`, `type`, `value`) " +
							"VALUES (?,?,?)",
					Statement.RETURN_GENERATED_KEYS);
			insert.setInt(1, node.hashCode());
			insert.setString(2, node.type);
			insert.setString(3, node.toCanonicalString());
			insert.executeUpdate();
			ResultSet rs = insert.getGeneratedKeys();  
		    rs.next();  
		    return (int)rs.getLong(1); 
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	@Override
	public void addEdge(Node from, Node to) {
		int fromID = getVertexIDAndInsert(from);
		int toID = getVertexIDAndInsert(to);
		try {
			PreparedStatement insert = con.prepareStatement(
					"INSERT INTO " + edgeTable + " (`fromID`, `toID`, `weight`) " +
							"VALUES (?,?,1) " + 
							"ON DUPLICATE KEY UPDATE weight=weight+1"
					);
			insert.setInt(1, fromID);
			insert.setInt(2, toID);
			insert.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean hasVertex(Node node) {
		return getVertexID(node) != null;
	}
	
	private Integer getVertexID(Node node) {
		try {
			PreparedStatement select = con.prepareStatement(
					"SELECT `id` FROM " + nodeTable + " AS nodes " +
							"WHERE nodes.hash = ? AND nodes.value = ?"
					);
			select.setInt(1, node.hashCode());
			select.setString(2, node.toCanonicalString());
			ResultSet query = select.executeQuery();
			if (!query.next()) return null;
			return query.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Iterable<Hint> getHints(Node node) {
		
		final List<Hint> hints = new ArrayList<Hint>();
		
		try {
			Integer id = getVertexID(node);
			if (id != null) {
				PreparedStatement select = con.prepareStatement(
						"SELECT value, weight FROM " + 
								nodeTable + " AS `nodes`, " + edgeTable + " AS edges " +
								" WHERE edges.fromID = ? AND nodes.id = edges.toID"
						);
				select.setInt(1, id);
				ResultSet query = select.executeQuery();
				while (query.next()) {
					String hint = query.getString(1);
//					int weight = query.getInt(2);
					hints.add(new Hint(node, new Node(null, hint)));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return hints;
	}

}
