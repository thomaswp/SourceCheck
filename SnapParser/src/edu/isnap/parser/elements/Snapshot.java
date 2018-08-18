package edu.isnap.parser.elements;

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

import edu.isnap.parser.elements.BlockDefinitionGroup.BlockIndex;
import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.parser.elements.util.XML;

public class Snapshot extends Code implements IHasID {
	private static final long serialVersionUID = 1L;

	private final static String META =
			"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=US-ASCII\">";

	public final String guid;
	public final String name;
	public final Stage stage;
	public final BlockDefinitionGroup blocks;
	public final List<BlockDefinition> editing = new ArrayList<>();
	public final List<String> variables = new ArrayList<>();

	@Override
	public String type() {
		return "snapshot";
	}

	@Override
	public String value() {
		return null;
	}

	private void setEditing() {
		Map<Integer, BlockDefinitionGroup> blockLists =
				BlockDefinitionGroup.getBlockDefGroups(this);
		for (int spriteIndex : blockLists.keySet()) {
			blockLists.get(spriteIndex).setEditingAndIndices(spriteIndex, editing);
		}
	}

	@SuppressWarnings("unused")
	private Snapshot() {
		this(null, null, null, null);
	}

	public Snapshot(String guid, String name, Stage stage, List<BlockDefinition> editing) {
		this.guid = guid;
		this.name = name;
		this.stage = stage;
		if (editing != null) this.editing.addAll(editing);
		this.blocks = new BlockDefinitionGroup(getID());
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
			// Sometimes snap files have newlines in their string literals and this can get messy
			xmlSource = xmlSource.replace("\r", "").replace("\n", "");
			xmlSource = xmlSource.replace(META, "");

			StringReader reader = new StringReader(xmlSource);

			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
					new InputSource(reader));
			doc.getDocumentElement().normalize();
			Element project = (Element) doc.getElementsByTagName("project").item(0);
			if (project == null) return null;

			XML.buildRefMap(project, "sprite");

			Element stage = XML.getFirstChildByTagName(project, "stage");

			// The guid may be in the project or the stage, depending on the iSnap version
			String guid = project.getAttribute("guid");
			if (guid.length() == 0) {
				guid = stage.getAttribute("guid");
			}

			List<BlockDefinition> editingBlocks = new ArrayList<>();
			Element editing = XML.getFirstChildByTagName(project, "editing");
			if (editing != null && editing.hasChildNodes()) {
				String defaultGUID = editing.getAttribute("guid");
				if (defaultGUID.isEmpty()) defaultGUID = null;
				for (Element scripts : XML.getChildrenByTagName(editing, "scripts")) {
					editingBlocks.add(BlockDefinition.parseFromScripts(scripts, defaultGUID));
				}
				for (Element editingDefinition : XML.getChildrenByTagName(editing, "block-definition")) {
					editingBlocks.add(BlockDefinition.parse(editingDefinition));
				}
			}

			Snapshot snapshot = new Snapshot(guid, name, Stage.parse(stage), editingBlocks);
			for (Code code : XML.getCodeInFirstChild(project, "blocks")) {
				snapshot.blocks.add((BlockDefinition) code);
			}
			for (Element variable : XML.getGrandchildrenByTagName(
					project, "variables", "variable")) {
				snapshot.variables.add(variable.getAttribute("name"));
			}

			snapshot.setEditing();

			// Unparsed children:
			// <code>: Snap-to-code mappings to translating Snap
			// <header>: Snap-to-header mappings for translating Snap
			// <hidden>: blocks which are hidden from view

			return snapshot;
		} catch (Exception e) {
			System.out.println("Error parsing: " + name);
			System.out.println(xmlSource);
			e.printStackTrace();
		} finally {
			XML.clearRefMap();
		}
		return null;
	}

	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add("Snapshot")
		.indent()
		.add(stage)
		.add("blocks:")
		.indent()
		.add(blocks.getWithEdits(canon))
		.close()
		.add(variables.size() == 0 ?
				null :
				("variables: " + variables.toString() + "\n"))
		.ifNotCanon()
		.add("editing:")
		.indent()
		.add(editing)
		.endIf()
		.close()
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		ac.add(stage);
		// TODO: Find a better way to determine whether to collapse the editing block
		// (And when you do make the same change in Sprite)
		ac.add(blocks.getWithEdits(true));
		addVariables(ac, variables);
	}

	@Override
	public String getID() {
		return guid;
	}

	public BlockIndex getEditingIndex(String name, String type, String category) {
		name = BlockDefinition.extractInputs(name, null);

		BlockIndex index = null;
		Map<Integer, BlockDefinitionGroup> blockLists =
				BlockDefinitionGroup.getBlockDefGroups(this);

		for (int spriteIndex : blockLists.keySet()) {
			BlockDefinitionGroup blocks = blockLists.get(spriteIndex);
			BlockIndex i = blocks.getEditingIndex(spriteIndex, name, type, category);
			if (i != null) {
				if (index != null) {
					// This can happen, but new data has GUIDs and should not worry about it
					// and old data has been investigated
//					System.err.println("Multiple matching indices!");
				}
				index = i;
			}
		}

		if (index == null) {
			// This can happen, but new data has GUIDs and should not worry about it
			// and old data has been investigated
//			System.err.printf("Not found: %s %s %s:\n", name, type, category);
		}

		return index;
	}


}
