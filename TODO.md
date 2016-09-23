# SnapHints

This repository contains projects for parsing Snap logs, generating data-driven hints, serving them to a client, and evaluating their quality.

## Setup

To use this repository, you will need a dataset. These are not versioned for privacy reasons. When you have a dataset (in .csv format), place in the data/csc200 folder. This file contains output from a single table with all logs.

You then need to find or create a corresponding dataset class in the [Assignment](Parser/src/com/snap/parser/Assignment.java) file. See the existing datasets for examples (e.g. `Fall2016`).

Next, open the [LogSplitter](Parser/src/com/snap/parser/LogSplitter.java) class, scroll to the bottom and edit the main method:

    public static void main(String[] args) throws IOException {
        // Replace "Fall2015" with the dataset you want to load
		splitStudentRecords(Assignment.Fall2015.dataFile);
	}

Run this file to separate the single log file into more a more manageable set of logs files for individual assignment attempts. It will take a while, and you should only ever need to run it once. When it is finished, check the `data/csc200/{dataset}/parsed` folder. You should see folders for each assignment in your dataset, containing .csv files representing each assignment attempt in the dataset.

## Parser

The Parser project contains files for parsing logs from iSnap. [SnapParser.java](Parser/src/com/snap/parser/SnapParser.java) will parse assignments for a given dataset; however, it is much easier to use the load method of the [Assignment](Parser/src/com/snap/parser/Assignment.java) you want to parse. This method will return a map of attemptIDs to [AssignmentAttempts](Parser/src/com/snap/parser/AssignmentAttempt.java). The AssignmentAttempt contains a list of actions that the student made during the attempt, such as interface or edit actions within the Snap interface.

Each action the student made may have an associated [Snapshot](Parser/src/com/snap/data/Snapshot.java). This is a Java data-structure representing the hierarchy of an exported Snap project (which is origianlly represented in XML). The basic structure of a Snapshot is as follows:

* **Snapshot**: The high-level structure for an entire Snap project.
  * *Variables*: Global variables for the project.
  * **Stage**: The background Sprite for a project. While the stage is itself a sprite, it also is that parent of all other Sprites.
    * *Sprite Members*: Stages contains all members of a Sprite, defined below:
    * **Sprites**: Scriptable actors on the Snap stage.
      * *Variables*: Local variables for this sprite.
      * **Scripts**: Executable code fragments.
        * **Blocks**: Code blocks (vertically alligned) in this Scriptable
          * *Blocks and Scripts*: Depending on the type of block, it may contained additional Blocks and Scripts nested inside.
      * **Block Definitions**: Custom blocks just for this Sprite
        * *Inputs*: Parameters for the custom block.
        * **Script**: The primary script the executes when this block is run.
        * **Scripts**: Additional scripts that are held in the custom block (they won't run).
  * **Block Definitions**: Global custom blocks

## Code Style