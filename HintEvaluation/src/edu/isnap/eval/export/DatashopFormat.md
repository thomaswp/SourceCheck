# Datashop Format

The PSLC Datashop was designed to store interaction data from intelligent tutors, but it also can be made to suit our needs. The input is a tab-separated text document (though I've attached a .csv for convenience). A dataset is represented with a single file, which contains data for multiple students and assignments, though the example I've attached only includes a single submission.

## Log Format
The [full spec](https://pslcdatashop.web.cmu.edu/help?page=importFormatTd) can be found here, but here are the columns I've chosen to include:

* Anon Student Id: A anonymous identifier for the student. Here, this is a GUID for the assignment attempt, but doesn't necessarily link students across assignments.
* Session Id: The session when the work was attempted. Here, this is just a GUID for the browser session when the work occurred.
* Time: A unix timestamp for when a given event occured.
* Student Response Type: Either ATTEMPT or HINT_REQUEST. This isn't entirely applicable to an open programming problem, so I've just put ATTEMPT for all edits that aren't hint requests.
* Level (Type): The type of assignment being worked on.
* Problem Name: The name of the problem being worked on.
* Step Name: A combination of the Aciton_Selection, with and IDs removed.
* Selection: The interface item being interacted with. Here, this depends on the next field, but usually reference the ID of a code element the student is interacting with.
* Action: THe event the occurred. I have quite a few unique events that can occur, but most of them reference blocks (code elements) being created and moved.
* Feedback Text: If a hint is given, what it said. Here, this is actually a JSON data structure. See the Feedback Text section below for more information.
* Feedback Classification: The type of feedback provided. Here, this is the same as Action, and it differentiates among hints for code bodies (script hints) and parameters (block hints), as well as structure hints, for feedback about the number of variables/custom blocks/etc.
* CF (AST): An abstract syntax tree representation of the student's code at the time of an event. See the next sections for more details.

## Snap Project structure

This data is from [Snap](http://snap.berkeley.edu/), which features drag-and-drop block-based programming and visual output. Snap projects have the following hierarchical elements:

* **Snapshot**: The high-level structure for an entire Snap project.
  * *Variables*: Global variables for the project.
  * **Stage**: The background Sprite for a project. While the stage is itself a sprite, it also is that parent of all other Sprites.
    * *Sprite Members*: Stages contains all members of a Sprite, defined below:
    * **Sprites**: Scriptable actors on the Snap stage.
      * *Variables*: Local variables for this sprite.
      * **Scripts**: Executable code fragments.
        * **Blocks**: Code blocks (vertically alligned) in this Script
          * *Blocks and Scripts*: Depending on the type of block, it may contained additional Blocks and Scripts nested inside.
      * **Block Definitions**: Custom blocks just for this Sprite
        * *Inputs*: Parameters for the custom block.
        * **Script**: The primary script the executes when this block is run.
        * **Scripts**: Additional scripts that are held in the custom block (they won't run).
  * **Block Definitions**: Global custom blocks

## ASTs

The AST representation used is fairly straightforward. It uses JSON to represent the tree structure. Each node of the tree will have some of the following core attributes:

* type (required): The type of node, usually one of the above elements.
* id: Most code elements will have a unique ID that allows identification across snapshots. The ID is not *guaranteed* to stay the same across consecutive snapshots (e.g. a variable name changing will change its id), but they are usually stable. If the Selection column reference an ID, it should be present in the AST of the same row.
* children: Any child nodes nested under this one.

Additionally, some elements will have special attributes:

* variableIDs: The IDs of any variables declared inside this node. For snapshots and sprites, these refer to global and local variables respectively, and for customBlocks they refer to input ("parameters") of the block.
* blockType: For callBlocks, the blockType tells you the type of block. This is akin to a function name in other languages. The blocks are all built into the Snap API and control Sprite.
* customBlockRef: If a callBlock has the "executeCustomBlock" blockType, it will also have this reference to the customBlock that it is calling.
* varRef: Nodes with "var" type will have this reference to the variable they represent. Note that these nodes also have their own ID, which identifies the individual variable block (that the user can move around), and multiple such "var" block can reference the same variable.
* value: A literal block may have a value. This will only be true for integers and hard-coded values, since free-text input can't be guaranteed to be unidentifiable.

## Feedback Text

All hints instruct the student to change the children under one node of the AST. When present, the Feedback Text colum will contain a JSON object with the following fields:
* parentID: the ID of the parent AST node, the children of which the hint directs the user to change. If the parent is a `script` node, it will not have an ID, in which case this refers to the script's parent node.
* parentType: the type of the parent node in the AST (e.g. snapshot, callBlock, etc.).
* scriptIndex: if the parent is a script, this gives the child index of that script under it's parent, so that the script can be exactly identified using this value and the parentID.
* from: the current list of children of the parent node in the AST.
* to: the recommended list of children of the parent node in the AST, as suggested by the hint.
* message: the actual text shown to the user, in the case of a "structure hint," which gives text rather than showing what blocks to change.