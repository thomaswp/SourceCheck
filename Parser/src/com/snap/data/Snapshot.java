package com.snap.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.snap.XML;

public class Snapshot extends Code implements IHasID {
	private static final long serialVersionUID = 1L;
	
	private final static String META = 
			"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=US-ASCII\">";
	
	public final String name;
	public final Stage stage;
	public final BlockDefinition editing;
	public final List<BlockDefinition> blocks = new ArrayList<BlockDefinition>();
	public final List<String> variables = new ArrayList<String>();

	@SuppressWarnings("unused")
	private Snapshot() {
		this(null, null, null);
	}
	
	public Snapshot(String name, Stage stage, BlockDefinition editing) {
		this.name = name;
		this.stage = stage;
		this.editing = editing;
	}
	
	public static Snapshot parse(File file) throws FileNotFoundException {
		if (!file.exists()) return null;
		Scanner sc = new Scanner(file);
		String xmlSource = "";
		while (sc.hasNext()) xmlSource += sc.nextLine();
		sc.close();
		if (xmlSource.length() == 0) return null;
		return parse(file.getName(), xmlSource);
	}
	
	public static Snapshot parse(String name, String xmlSource) {
		if (xmlSource == null || xmlSource.length() == 0) return null;
		try {
			xmlSource = xmlSource.replace(META, "");
			StringReader reader = new StringReader(xmlSource);
//			System.out.println(xmlSource);
			
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));
			doc.getDocumentElement().normalize();
			Element project = (Element) doc.getElementsByTagName("project").item(0);
			if (project == null) return null;
			
			XML.buildRefMap(project, "sprite");
			
			Element stage = XML.getFirstChildByTagName(project, "stage");
			
			BlockDefinition editingBlock = null;
			Element editing = XML.getFirstChildByTagName(project, "editing");
			if (editing != null && editing.hasChildNodes()) {
				editingBlock = BlockDefinition.parseEditing(editing);
			}
			
			Snapshot snapshot = new Snapshot(name, Stage.parse(stage), editingBlock);
			for (Code code : XML.getCodeInFirstChild(project, "blocks")) {
				snapshot.blocks.add((BlockDefinition) code);
			}
			for (Element variable : XML.getGrandchildrenByTagName(project, "variables", "variable")) {
				snapshot.variables.add(variable.getAttribute("name"));
			}
			
			XML.ensureEmpty(project, "headers", "code");
			// TODO: what is in <hidden>?
			return snapshot;
		} catch (Exception e) {
			System.out.println("Error parsing: " + name);
			System.out.println(xmlSource);
			e.printStackTrace();
		}
		return null;
	}
	
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add("Snapshot")
		.indent()
		.add(stage)
		.add("blocks:")
		.indent()
		.add(blocks)
		.close()
		.add(variables.size() == 0 ? null : ("variables: " + canonicalizeVariables(variables, canon).toString() + "\n"))
		.add("editing:")
		.indent()
		.add(editing)
		.close()
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(stage);
		ac.add(blocks);
		ac.add(canonicalizeVariables(variables, canon));
		if (editing != null) ac.add(editing);
		return canon ? "snapshot" : name;
	}
	
	@Override
	public Object getID() {
		return "snapshot";
	}
}
