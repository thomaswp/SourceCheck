
# SnapParser

The Parser project contains files for parsing logs from iSnap. [SnapParser](src/edu/isnap/parser/SnapParser.java) will parse assignments for a given dataset; however, it is much easier to use the load method of the [Assignment](src/edu/isnap/parser/dataset/Assignment.java) you want to parse. This method will return a map of attemptIDs to [AssignmentAttempts](src/edu/isnap/parser/datasets/AssignmentAttempt.java). The AssignmentAttempt contains a list of actions that the student made during the attempt, such as interface or edit actions within the Snap interface.

Each action the student made may have an associated [Snapshot](src/edu/isnap/parser/elements/Snapshot.java). This is a Java data-structure representing the hierarchy of an exported Snap project (which is originally represented in XML). The basic structure of a Snapshot is as follows:

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