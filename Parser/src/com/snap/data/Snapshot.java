package com.snap.data;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import com.snap.XML;

public class Snapshot extends Code {
	
	private final static String META = 
			"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=US-ASCII\">";
	
	public final String name;
	public final Stage stage;
	public final List<BlockDefinition> blocks = new ArrayList<BlockDefinition>();
	public final List<String> variables = new ArrayList<String>();
	
	public Snapshot(String name, Stage stage) {
		this.name = name;
		this.stage = stage;
	}
	
	public static Snapshot parse(File file) {
		if (!file.exists()) return null;
		try {
			Scanner sc = new Scanner(file);
			String xmlSource = "";
			while (sc.hasNext()) xmlSource += sc.nextLine();
			sc.close();
			if (xmlSource.length() == 0) return null;
			xmlSource = xmlSource.replace(META, "");
			StringReader reader = new StringReader(xmlSource);
//			System.out.println(xmlSource);
			
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));
			doc.getDocumentElement().normalize();
			Element project = (Element) doc.getElementsByTagName("project").item(0);
			if (project == null) return null;
			Element stage = XML.getFirstChildByTagName(project, "stage");
			Snapshot snapshot = new Snapshot(file.getName(), Stage.parse(stage));
			for (Code code : XML.getCodeInFirstChild(project, "blocks")) {
				snapshot.blocks.add((BlockDefinition) code);
			}
			for (Element variable : XML.getGrandchildrenByTagName(project, "variables", "variable")) {
				snapshot.variables.add(variable.getAttribute("name"));
			}
			XML.ensureEmpty(project, "headers", "code");
			// TODO: what is in <hidden>?
			return snapshot;
		} catch (SAXParseException e) {
			System.err.println("Failed to parse " + file.getAbsolutePath());
			file.renameTo(new File(file.getAbsoluteFile() + ".fail"));
		} catch (Exception e) {
			System.err.println("Failed to parse " + file.getAbsolutePath());
			e.printStackTrace();
		}
		return null;
	}
	
	public String toCode() {
		return new CodeBuilder()
		.add("Snapshot")
		.indent()
		.add(stage)
		.add("blocks:")
		.indent()
		.add(blocks)
		.close()
		.add(variables.size() == 0 ? null : ("variables: " + variables.toString() + "\n"))
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(stage);
		ac.add(blocks);
		ac.add(canonicalizeVariables(variables, canon));
		return "snapshot";
	}
}
