package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BlockDefinitionGroup {

	public final List<BlockDefinition> blocks = new ArrayList<>();
	private final String parentID;
	private List<BlockDefinition> editing;

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

	public void setEditingAndIndices(int spriteIndex, List<BlockDefinition> editing) {
		for (int i = 0; i < blocks.size(); i++) {
			blocks.get(i).blockIndex = new BlockIndex(spriteIndex, i);
		}
		this.editing = editing;
	}

	public List<BlockDefinition> getWithEdits(boolean collapseEditing) {
		if (!collapseEditing) {
			return blocks;
		}

		List<BlockDefinition> editBlocks = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			if (blocks.get(i).isImported) continue;

			BlockDefinition definition = blocks.get(i);
			// Look through editing blocks for a match
			for (BlockDefinition editingBlock : editing) {
				boolean match;
				if (editingBlock.guid == null && editingBlock.blockIndex != null) {
					// If the block has no GUID and we've set a blockIndex match on that
					match = editingBlock.blockIndex.equals(definition.blockIndex);
				} else {
					// Otherwise use the GUID
					match = editingBlock.guid.equals(definition.guid);
				}
				if (match) {
					// If it matches, replace the definition and break
					definition = editingBlock;
					break;
				}
			}
			editBlocks.add(definition);
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
		Map<Integer, BlockDefinitionGroup> blockLists = new TreeMap<>();
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

		public final int spriteIndex;
		public final int blockDefIndex;

		public BlockIndex(int spriteIndex, int blockDefIndex) {
			this.spriteIndex = spriteIndex;
			this.blockDefIndex = blockDefIndex;
		}

		public boolean equals(BlockIndex index) {
			return index != null &&
					index.spriteIndex == spriteIndex &&
					index.blockDefIndex == blockDefIndex;
		}

		@Override
		public String toString() {
			return String.format("[%d,%d]", spriteIndex, blockDefIndex);
		}
	}
}
