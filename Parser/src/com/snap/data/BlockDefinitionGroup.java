package com.snap.data;

import java.util.ArrayList;
import java.util.List;

public class BlockDefinitionGroup {
	
	public final List<BlockDefinition> blocks = new ArrayList<BlockDefinition>();
	
	public int editingIndex;
	public BlockDefinition editing;
	
	public void add(BlockDefinition block) {
		blocks.add(block);
	}

	public List<BlockDefinition> getWithEdits(boolean canon) {
		if (!canon || editing == null || editingIndex == -1) {
			return blocks;
		}
		
		List<BlockDefinition> editBlocks = new ArrayList<BlockDefinition>();
		for (int i = 0; i < blocks.size(); i++) {
			if (i == editingIndex) {
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
	
	public static class BlockIndex {
		public static final int STAGE_INDEX = -1;
		public static final int SNAPSHOT_INDEX = -2;
		public static final int NONE_INDEX = -3;
		
		public final int spriteIndex;
		public final int blockDefIndex;
		
		public BlockIndex(int spriteIndex, int blockDefIndex) {
			this.spriteIndex = spriteIndex;
			this.blockDefIndex = blockDefIndex;
		}
		
		public BlockDefinitionGroup getGroup(Snapshot snapshot) {
			if (spriteIndex == SNAPSHOT_INDEX) {
				return snapshot.blocks;
			} else if (spriteIndex == STAGE_INDEX) {
				return snapshot.stage.blocks;
			} else {
				return snapshot.stage.sprites.get(spriteIndex).blocks;
			}
		}
		
		public BlockDefinition get(Snapshot snapshot) {
			return getGroup(snapshot).blocks.get(blockDefIndex);
		}
		
		public void setEditing(Snapshot snapshot, BlockDefinition editing) {
			getGroup(snapshot).editing = editing;
		}
	}
}
