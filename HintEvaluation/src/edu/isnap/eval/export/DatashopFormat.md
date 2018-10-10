# Datasets

This data contains detailed log data of students' use of the [iSnap](http://go.ncsu.edu/isnap) programming environment for an introductory undergraduate computing course for non-majors. The course content focused on computer applications, but also included a once-per-week lab section where students learned to program, using a curriculum loosely based on the [Beauty and Joy of Computing](http://bjc.berkeley.edu/). The data includes data from both in-lab assignments, where students worked in the lab sections with a TA available for help, and independent homework assignments. 

## Assignments

Datasets contain data from the following assignments:

### Lights, Camera Action (Homework)

ID: lightsCameraActionHW

In this activity you will create a short story using SNAP!
Your short story should have, at least:
* A beginning, a middle, and an end
* One block that we did not use during lab (Explore your blocks)
* Two unique and different sprites that interact with each other.
  * Neither sprite may be the default turtle
* Ten total broadcasts of messages between the sprites
  * Must be at least 10 "broadcast" blocks

The activity is open to your creativity - tell a story, create characters, and experiment with moving sprites around stage.

### Polygon Maker (In-Lab)

ID: polygonmakerLab

Step 1:

Create a new block that can draw any polygon.  
It should have a total of 3 input areas for:
* the size of each side
* the number of sides
* how thick the pen is

Step 2:

Perform these tests:

1. \# of sides = 4, size = length of 100, thickness = 5
  * This should draw a square
2. \# of sides = 10, size = length of 50, thickness = 6
  * This should draw a decagon
3. \# of sides = 50, size = length of 7, thickness = 2
  * What do you think this will draw?

### Squiral (Homework)

ID: squiralHW

In this activity you will build a block, in SNAP, that makes your sprite draw a squiral like [the one below](http://moodle.kkc.school.nz/file.php/203/Images/Lab01/Squiral.jpg).

### Guessing Game Part 1 (In-Lab)

ID: guess1Lab

For the next few weeks you will be designing a guessing game.  This activity will get you started. 

Item 1: The computer chooses a random number between 1 and 10 and continuously asks the user to guess the number until they guess correctly.
Item 2:

Make sure that your program contains the following:
* Welcome the user to the game
* Ask the user's name
* Welcome the user by name
* Tell the user if their guess was too high, too low, or correct

### Guessing Game Part 2 (Homework)

ID: guess2HW

In this activity you will expand upon the guessing game you made in class.

Please make sure that all items from Activity 3 are included in this assignment, as well as the following:
* Allow the user to choose minimum and maximum numbers that the random number will be between
* Keep track of how many guesses the user used
* Report the number of guesses when the user wins

### Guessing Game Part 3 (In-Lab)

ID: guess3Lab

This week you will be recreating your guessing game with a twist: The user will be choosing the secret number and the sprite will be guessing.
Your final version of the Guessing Game should include:
1. The sprite asks the user for the minimum and maximum numbers for the range
2. The user must pick a number for the sprite to guess in that range
  * You are choosing this in your head, not creating script. You might want to write this down just in case you forget the number you choose. 
3. Make the sprite guess numbers until it guesses correctly.
  * This means the sprite is guessing until the user tells it that it is right
4. Make a list that stores all of the guesses made by the sprite and use it to keep the sprite from repeating guesses.
  * The sprite should never make the same guess twice
  * The list should reset after every game

# Datashop Format

The PSLC Datashop was designed to store interaction data from well-structured intelligent tutors, but this dataset uses the platform to share open programming data. The input is a tab-separated text document. A dataset is represented with a single file, which contains data for multiple students and assignments, though the example I've attached only includes a single submission.

## Log Format
The [full spec](https://pslcdatashop.web.cmu.edu/help?page=importFormatTd) can be found here, but here are the columns I've chosen to include:

* Anon Student Id: A anonymous identifier for the "student id". Here, this is actually a GUID for the assignment attempt, but doesn't necessarily link students across assignments. **Note**: this causes Datashops' student count to incorrectly count students once per assignment, giving largely inflated numbers.  
* Session Id: The session when the work was attempted. Here, this is just a GUID for the browser session when the work occurred.
* Time: A unix timestamp (in milliseconds) for when a given event occurred.
* Student Response Type: Either ATTEMPT or HINT_REQUEST. This isn't entirely applicable to an open programming problem, so I've just put ATTEMPT for all edits that aren't hint requests.
* Level (Type): The type of assignment being worked on.
* Problem Name: The name of the problem being worked on.
* Step Name: A combination of the Aciton_Selection, with and IDs removed.
* Selection: The interface item being interacted with. Here, this depends on the next field, but usually reference the ID of a code element the student is interacting with.
* Action: The event the occurred. I have quite a few unique events that can occur, but most of them reference blocks (code elements) being created and moved.
* Input: Duplicate of Action, since students' inputs are always actions. This is a workaround for a datashop bug.
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

All hints instruct the student to change the children under one node of the AST. When present, the Feedback Text column will contain a JSON object with the following fields:
* parentID: the ID of the parent AST node, the children of which the hint directs the user to change. If the parent is a `script` node, it will not have an ID, in which case this refers to the script's parent node.
* parentType: the type of the parent node in the AST (e.g. snapshot, callBlock, etc.).
* scriptIndex: if the parent is a script, this gives the child index of that script under it's parent, so that the script can be exactly identified using this value and the parentID.
* from: the current list of children of the parent node in the AST.
* to: the recommended list of children of the parent node in the AST, as suggested by the hint.
* message: the actual text shown to the user, in the case of a "structure hint," which gives text rather than showing what blocks to change.