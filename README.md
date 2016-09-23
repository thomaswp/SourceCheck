# SnapHints

This repository contains projects for parsing Snap logs, generating data-driven hints, serving them to a client, and evaluating their quality. Project dependencies are as follows:

* SnapParser
  * Graph Analysis
    * Hint Server
    * Hint Evaluation

## Setup

The projects here are designed for use with [Eclipse](http://www.eclipse.org/). For the HintSerer to work properly, you may need to select the "EE" version of Eclipse, or install the [Web Tools Platform](http://www.eclipse.org/webtools/). 

Once you have cloned this repo, open Eclipse and choose File->Import->Existing Projects Into Workspace and select the root folder of this repository. Select all projects and import them.

### Data

To use this repository, you will need a dataset. These are not versioned for privacy reasons. When you have a dataset (in .csv format), place in the data/csc200 folder. This file contains output from a single table with all logs.

You then need to find or create a corresponding dataset class in the [Assignment](Parser/src/com/snap/parser/Assignment.java) file. See the existing datasets for examples (e.g. `Fall2016`).

Next, open the [LogSplitter](Parser/src/com/snap/parser/LogSplitter.java) class, scroll to the bottom and edit the main method:

    public static void main(String[] args) throws IOException {
        // Replace "Fall2015" with the dataset you want to load
        splitStudentRecords(Assignment.Fall2015.dataFile);
    }

Run this file to separate the single log file into more a more manageable set of logs files for individual assignment attempts. It will take a while, and you should only ever need to run it once. When it is finished, check the `data/csc200/{dataset}/parsed` folder. You should see folders for each assignment in your dataset, containing .csv files representing each assignment attempt in the dataset.

### Code Style

The code in this repository is in various states of cleanliness, but new code should endeavor to be as clean as possible. This repository uses standard Java code style, and it is recommended you enable some settings in Eclipse to maintain this:

* Preferences->General->Editors->Text Editors->Print margin columns: Set this value to 100 and for new code, try to keep lines under 100 characters in length.
* Preferences->Java->Editor->Save Actions: Check the "Perform the selected action on save" box, and uncheck "Format source code," but check "Organize Imports" and "Additional Actions." Click "Configure..." and check "Remove trailing whitespace." This will ensure your code does not have excess tabs/spaces and that it uses only needed imports. 

## Data Folders

When you have set up your dataset under `data/csc200/{dataset}/`, you will see some folders created as your run code:

* **parsed**: This contains the parsed .csv files from splitting the large dataset file. It also contains cached versions of these .csv files for easier loading. To remove the cached files easily, you can run `Parser.clean("../data/csc200/{dataset}/parsed")`.  
* **grades**: If you have created manual grades for an assignment, it should go here. This will be parsed with the snapshots.
* **submitted**: A folder for submitted assignments, actually turned into the teacher. If you create folders and fill them with submitted .xml files for each assignment, then run [ParseSubmitted](Parser/src/com/snap/parser/ParseSubmitted.java), it will generate a text file with the list of GUIDs of submitted assignments. This file can be versioned, and is used when parsing assignment attempts to flag them as submittetd or not.
* **graphs**: After creating a [HintGenerator](GraphAnalysis/src/com/snap/graph/subtree/HintGenerator.java), you can call the `createGraphs` method to generate a set of .graphml files, one for each node in the ASTs oberved. These can be opened with [yEd](https://www.yworks.com/products/yed), and the formatted using Layout->Hierarchical. This is helpful for debugging. This method will also generate text files for each node, which show the goal states for that node, and their comparative rankings.
* **analysis**: Most scripts in the HintEvalutaion project will produce a .csv file in the analysis folder. These can be read in using the R scripts in the R folder.
* **unittests**: See the Unit Tests section under GraphAnalysis.

## Parser

The Parser project contains files for parsing logs from iSnap. [SnapParser](Parser/src/com/snap/parser/SnapParser.java) will parse assignments for a given dataset; however, it is much easier to use the load method of the [Assignment](Parser/src/com/snap/parser/Assignment.java) you want to parse. This method will return a map of attemptIDs to [AssignmentAttempts](Parser/src/com/snap/parser/AssignmentAttempt.java). The AssignmentAttempt contains a list of actions that the student made during the attempt, such as interface or edit actions within the Snap interface.

Each action the student made may have an associated [Snapshot](Parser/src/com/snap/data/Snapshot.java). This is a Java data-structure representing the hierarchy of an exported Snap project (which is originally represented in XML). The basic structure of a Snapshot is as follows:

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

## GraphAnalysis

This project contains the core AI of the data-driven hint generation process. The most important class here is the [SnapHintBuilder](GraphAnalysis/src/com/snap/graph/subtree/SnapHintBuilder.java). It is used to construct a [HintGenerator](GraphAnalysis/src/com/snap/graph/subtree/HintGenerator.java) from Snap data, using the SnapParser discussed earlier. This is done with the `buildGenerator` method. Additionally, the `main` method of SnapHintBuilder can be easily used to build a HintGenerator for a number of assignments, cache them, and copy them over to the HintServer for use with iSnap. 

The HintGenerator itself is non-Snap-specific, and therefore uses a generic AST data-structure, called a [Node](GraphAnalysis/src/com/snap/graph/data/Node,java). The node is just a basic tree node class, but it contains many convenience methods for searching through the tree. The HintGenerator deals constructing the data-structure behind hint generation, and its primary logic involved identifying when two Nodes match in consecutive snapshots. It builds up a [HintFactoryMap](GraphAnalysis/src/com/snap/graph/data/HintFactoryMap.java) for each assignment attempt, and later combines them into a single map representing all attempts. This class contains the real logic behind the [CTD algorithm](http://www4.ncsu.edu/~twprice/website/files/EDM%202016.pdf). Unsurprisingly, it makes use of interaction networks and the HintFactory aglorithm, which are implemented in the [InteractionGraph](GraphAnalysis/src/com/snap/graph/data/InteractionGraph.java) and [VectorGraph](GraphAnalysis/src/com/snap/graph/data/VectorGraph.java) classes.

### Unit Tests

Because the hint generation algorithm is modified on a regular basis, it is important to get an understanding of how these changes affect the generated hints. To do this, you can collect unit tests, representing good (or bad) hints, and check to make sure these tests still pass after modifying the algorithm.

The [RunTests](GraphAnalysis/src/com/snap/graph/unittest/RunTests.java) class will report on all tests for the dataset defined on its first line of code. Some tests will be "expected failures," meaning the behavior is not ideal, but a known issue. A failed test is not necessarily a bad thing - the new hint may be different but equally correct. In this case the test can be modified to match the new behavior. In this sense, these tests are not like normal unit tests, as they define a changing definition of expected behavior. They are also dataset-dependent, since the hints themselves will depend on the input data.

To create a Unit Test, in Eclipse edit the Run Confirguration for the HintServer (Run->Run Configurations...->Apache TomCat->{Your Instace}), select the Environment tab, add a new entry with variable name "dataDir", and value the full path to the dataset directory where you want to collect unit tests (e.g. "C:\...\SnapHints\data\csc200\spring2016"). Then run the HintServer and run iSnap. In iSnap, make sure the config is setup to use your local hint server and to show the DebugDisplay. Then, when you see a hint that you want in the DebugDisplay, hit the save button. It should now appear in the unittest folder of the dataset you're working with.

## HintServer

The HintServer is a Java servlet designed to serve hints to iSnap. To use the HintServer, you must generate hints using the [SnapHintBuilder](GraphAnalysis/src/com/snap/graph/subtree/SnapHintBuilder.java)'s main method. This will generate HintBuilders for each assignment, cache them, and copy the cached files to the HintServer's WEB-INF folder. If using Eclipse, you'll need to refresh the HintServer folder before starting the server, so that it recognizes the updated data files. 

## HintEvaluation

The HintEvaluation project contains a number of classes which perform automated, technical evaluations of the Snap hints. Some of these evaluations methods may be a bit dated, since they were designed for an older version of the algorithm.

Most evaluation logic can be found in the [com.snap.eval](HintEvaluation/src/com/snap/eval) package. These classes have main methods, which mainly produce .csv files in the analysis folder of a given dataset. These are used by the R scripts, found in the [R folder](R).

This would be an appropriate project to add additional evaluation scripts.
