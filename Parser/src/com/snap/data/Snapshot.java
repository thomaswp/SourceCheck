package com.snap.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.snap.XML;
import com.snap.data.BlockDefinitionGroup.BlockIndex;

public class Snapshot extends Code implements IHasID {
	private static final long serialVersionUID = 1L;
	
	private final static String META = 
			"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=US-ASCII\">";
	
	public final String name;
	public final Stage stage;
	public final BlockDefinition editing;
	public final BlockDefinitionGroup blocks = new BlockDefinitionGroup();
	public final List<String> variables = new ArrayList<String>();
	
	public void setEditingIndex(BlockIndex index) {
		if ((index == null) != (editing == null)) {
			System.err.println("Editing index exists iff editing exists");
		}
		
		Map<Integer, BlockDefinitionGroup> blockLists = 
				BlockDefinitionGroup.getBlockDefGroups(this);
		for (int spriteIndex : blockLists.keySet()) {
			blockLists.get(spriteIndex).setEditing(editing, index, spriteIndex);
		}
	}

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

			if (snapshot.editing != null && snapshot.editing.guid != null) {
				snapshot.setEditingIndex(new BlockIndex(snapshot.editing.guid));
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
		.add(blocks.getWithEdits(canon))
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
		ac.add(blocks.getWithEdits(canon));
		ac.add(canonicalizeVariables(variables, canon));
		return canon ? "snapshot" : name;
	}
	
	@Override
	public Object getID() {
		return "snapshot";
	}

	public BlockIndex getEditingIndex(String name, String type, String category) {
		if (editing == null) return null;
		if (editing.guid != null) return new BlockIndex(editing.guid);
		
		name = BlockDefinition.steralizeName(name);
		
		BlockIndex index = null;
		Map<Integer, BlockDefinitionGroup> blockLists = BlockDefinitionGroup.getBlockDefGroups(this);
		
		for (int spriteIndex : blockLists.keySet()) {
			BlockDefinitionGroup blocks = blockLists.get(spriteIndex);
			BlockIndex i = blocks.getEditingIndex(spriteIndex, name, type, category);
			if (i != null) {
				if (index != null) {
					System.err.println("Multiple matching indices!");
				}
				index = i;
			}
		}
		
		if (index == null) {
			System.err.printf("Not found: %s %s %s:\n", name, type, category);
		}
		
		return index;
	}
	
	
}
