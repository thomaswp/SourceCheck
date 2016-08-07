package com.snap.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BlockDefinitionGroup {

	public final List<BlockDefinition> blocks = new ArrayList<BlockDefinition>();
	private final String parentID;

	private int editingIndex = -1;
	private BlockDefinition editing;

	@SuppressWarnings("unused")
	private BlockDefinitionGroup() {
		this(null);
	}

	public BlockDefinitionGroup(String parentID) {
		this.parentID = parentID;
	}

	private String getParentID(int index) {
		return parentID + index;
	}

	public void add(BlockDefinition block) {
		blocks.add(block);
		block.parentID = getParentID(blocks.size() - 1);
	}

	public void setEditing(BlockDefinition editing, BlockIndex index, int spriteIndex) {
		editingIndex = -1;
		if (index != null) {
			editingIndex = guidIndex(index.guid);
			if (editingIndex == -1 && spriteIndex == index.spriteIndex) {
				editingIndex = index.blockDefIndex;
			}
		}

		this.editing = editingIndex == -1 ? null : editing;
		if (this.editing != null) this.editing.parentID = getParentID(editingIndex);
	}

	private int guidIndex(String guid) {
		for (int i = 0; i < blocks.size(); i++) {
			if (guid.equals(blocks.get(i).guid)) return i;
		}
		return -1;
	}

	public List<BlockDefinition> getWithEdits(boolean canon) {
		if (!canon) {
			return blocks;
		}

		List<BlockDefinition> editBlocks = new ArrayList<BlockDefinition>();
		for (int i = 0; i < blocks.size(); i++) {
			if (blocks.get(i).isToolsBlock) continue;
			if (editing != null && i == editingIndex) {
				editBlocks.add(editing);
			} else {
				editBlocks.add(blocks.get(i));
			}
		}
		return editBlocks;
	}

	public BlockIndex getEditingIndex(int spriteIndex, String name, String type, String category) {
		for (int i = 0; i < blocks.size(); i++) {
			BlockDefinition def = blocks.get(i);
			if (def.name.equals(name) && def.type.equals(type) && def.category.equals(category)) {
				return new BlockIndex(spriteIndex, i);
			}
		}
		return null;
	}

	public static Map<Integer, BlockDefinitionGroup> getBlockDefGroups(Snapshot snapshot) {
		Map<Integer, BlockDefinitionGroup> blockLists = new TreeMap<Integer, BlockDefinitionGroup>();
		blockLists.put(BlockIndex.SNAPSHOT_INDEX, snapshot.blocks);
		blockLists.put(BlockIndex.STAGE_INDEX, snapshot.stage.blocks);
		for (int i = 0; i < snapshot.stage.sprites.size(); i++) {
			blockLists.put(i, snapshot.stage.sprites.get(i).blocks);
		}
		return blockLists;
	}

	public static class BlockIndex {
		public static final int STAGE_INDEX = -1;
		public static final int SNAPSHOT_INDEX = -2;
		public static final int NONE_INDEX = -3;

		public final int spriteIndex;
		public final int blockDefIndex;
		public final String guid;

		public BlockIndex(String guid) {
			this.spriteIndex = NONE_INDEX;
			this.blockDefIndex = NONE_INDEX;
			this.guid = guid;
		}

		public BlockIndex(int spriteIndex, int blockDefIndex) {
			this.spriteIndex = spriteIndex;
			this.blockDefIndex = blockDefIndex;
			this.guid = null;
		}
	}
}
