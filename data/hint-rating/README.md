# Hint Rating Data

This is a specification for hint rating data used to benchmark and compare the quality of data-driven hints. This data is used as input for the QualityScore procedure defined in [1]. The datasets include 1) training data that can be used to create data-driven programming hints; 2) real student hint requests on which to test data-driven hints; and 3) gold standard expert hints, against which to compare these hints. Possible uses for this data include:
* Testing a newly developed data-driven hint generation algorithm on new datasets in different programming languages.
* Evaluating the quality of data-driven hints against the gold standard hints.
* Comparing the quality of one or more data-driven hint algorithms.
* Evaluating how factors such as the quantity of training data impact the quality of data-driven hints (e.g. [1]).
* Testing a new feature for a hint generation algorithm and seeing if it improves hint quality (though beware of overfitting to this dataset).

For the most up-to-date information, please see go.ncsu.edu/hint-quality-data. When source code for the QualityScore procedure is released, a link will be provided there.

## Analysis

The best way to make use of this data is through the [QualityScore](https://github.com/thomaswp/QualityScore]) GitHub repository, which provides code for reading this data and running the QualityScore procedure to rate the data.

## Datasets

There are currently 3 datasets, collected from two learning environments for introductory programming that offer data-driven hints: iSnap [2], a block-based programming environment and ITAP [3], an intelligent tutoring system (ITS) for Python programming. Both datasets consist of log data collected from students working on multiple programming problems, including complete traces of their code and records of hints they requested.

* **isnapF16-F17**: This  dataset was collected from an introductory programming course for non-majors during the Fall 2016, Spring 2017 and Fall 2017 semesters, with 171 total students and 61 hint requests over 2 assignments.
* **isnapF16-S17**: A subset of the isnapF16-F17 dataset that only includes the Fall 2016 and Spring 2017 semesters. This dataset was used in [1]. It consists of data from 120 total students and 47 hint requests over 2 assignments.
* **itapF16-F17**: This dataset was collected from two introductory programming courses in Spring 2016, with 89 total students and 51 hint requests over 5 assignments. (see [4] for details).

Each dataset contains the follow files and folders, which are explained in the following sections:

* gold-standard.csv
* training.csv
* requests.csv
* [snap | python]-grammar.json
* algorithms [folder]

## Training and Request Data

Each dataset contains a set of real student hint requests (requests.csv) and training data (training.csv) for use in generating hints. The iSnap datasets contains one randomly sampled hint request per problem (see the Problems section) from each student who used hints. This ensured that no student was overrepresented in the set of hint requests. The ITAP dataset contains up to two randomly sampled, unique hint requests from each student who used hints per problem, since there were fewer students who requested hints than in the iSnap dataset. This only includes hint requests where the student's Python code could be parsed, since this is required to generate an AST. Both datasets also include a set of training data, consisting of the traces of each student who submitted a correct solution and did not request hints.

In the training.csv spreadsheet, each row corresponds to one snapshot of code from the trace of a student who successfully completed the assignment. In the requests.csv spreadsheet, each row corresponds to one snapshot of code from the trace of a student who requested a hint. This includes all snapshots up until and including the hint requests, such that the final snapshot represents that state of the student's code at the time of the hint request. The columns of both spreadsheets are as follows:
* assignmentID: The ID of the assignment being worked on in this trace.
* traceID: A unique ID for the trace that this snapshot belongs to.
* index: The index of this snapshot within its trace.
* isCorrect: TRUE if the given snapshot is known to be correct. Note that in the Snap dataset, only submitted snapshots are graded (manually), so intermediate snapshots may be correct but still have this value as FALSE.
* source: For the ITAP dataset, this contains the Python source code used to generate the abstract syntax tree for this snapshot. The last snapshot in each trace of the training dataset is always correct.
* code: The JSON abstract syntax tree representing this snapshot (see the Abstract Syntax Tree Format section).

## Gold Standard Data

The gold-standard.csv file contains the human-tutor-authored gold standard hints, which are used to evaluate the quality of other, automatically-generated hints. The hints were generated in three phases (see [1] for details):
1. Three tutors independently reviewed each hint request in the dataset, including the history of the student's code before the hint request. Each tutor then generated a set of all hints they considered to be valid, useful, and not confusing. Each hint was represented as one or more edits to the student's code, making these hints comparable to the edit-based hints offered by many data-driven algorithms.  Tutors were instructed to limit their hints to one edit (e.g. one insertion) unless multiple edits were needed to avoid confusion. Hints were designed to be independently useful, with the understanding that the student would receive any *one* hint, not the whole set. The tutors were told that these edits would be communicated to the student without any natural language explanation, so the edits should be interpretable on their own.
2. Each tutor independently reviewed the hints generated by the other two tutors and marked each hint as valid or invalid by the same criteria used to generate hints. We included in our gold standard set any hint which at least two out of three tutors considered to be valid. Our goal was not to determine a definitive, objective set of correct hints but rather to identify a large number of hints that a reasonable human tutor might generate. Requiring that two tutors agreed on each hint provided a higher quality standard than is used in most classrooms, while allowing for differences of opinion among tutors.
3. *For the iSnap datasets only*, the tutors then reconvened to discuss any hints where there was disagreement and resolved these disagreements to produce a final consensus set of gold standard hints.

Each row of the spreadsheet corresponds to one hint authored by the tutors. The CSV has the following columns:

* assignmentID: The ID of the assignment of the hint request.
* requestID: A unique ID for the hint request for which the hint was authored.
* year: The semester when the hint request was made.
* hintID: A year-unique ID for the tutor-authored hint (only guaranteed unique for a given year).
* OneTutor: TRUE if at least one tutor believed this was a valid hint after Phase 2.
* MultipleTutors: TRUE if at least *two* tutors believed this hint was valid after Phase 2.
* Consensus [*Meaningful only for the iSnap dataset*]: TRUE if all tutors agreed that this was a valid hint after Phase 3.
* priority [*Meaningful only for the iSnap dataset*]: For hints where Consensus is TRUE, tutors also came to consensus on the priority of the hint: 1 (highest), 2 (high) or 3 (normal).
* from: A JSON abstract syntax tree for the student's code at the time of the hint request (see the next section for a description of the JSON AST format).
* to: A JSON abstract syntax tree for the student's code after applying the tutor's recommended hint.

## Abstract Syntax Tree JSON Format

All code in these datasets are represented as abstract syntax trees (ASTs), stored in a JSON format. Each JSON object represents a node in the AST, and has the following properties:
* `type` [required]: The type of the node (e.g. "if-statement", "expression", "variable-declaration", etc.). In Snap, this could be the name of a built-in block (e.g. "forward", "turn"). The set of possible types is pre-defined by a given programming language, as they generally correspond to keywords. The possible types for a given language are defined in the grammar file for the dataset, discussed later.
* `value` [optional]: This contains any user-defined value for the node, such as the identifier for a variable or function, the value of a literal, or the name of an imported module These are things the student names, and they could take any value. **Note**: In the Snap datasets, string literal values have been removed to anonymize the dataset; however, these values are generally not relevant for hint generation.
* `children` [optional]: A map of this node's children, if any. In Python, the keys of the map indicate the relationship of the parent/child (e.g. a while loop might have a "condition" child). In the Snap dataset, they are simply numbers indicating the ordering of the children (e.g. arguments "0", "1" and "2"). The values are objects representing the children.
* `children-order` [optional]: The order of this node's children, represented as an array of keys from the `children` map. This is necessary because JSON maps have no ordering, though the order of the children in the map should correspond to the correct order.
* `id` [optional]: A trace-unqiue ID for the node that will be kept constant across ASTs in this trace. This is useful in block-based languages, for example, to identify a given block, even if it moves within the AST.

### AST Comparisons

The QualityScore procedure must determine whether an algorithmically generated hint *matches* a tutor-authored gold standard hint. Both of these hints are represented as the AST that would result from applying the hint to the hint-requesting student's current code, so a straightforward approach is to simply compare these ASTs directly to determine if two hints are equivalent. However, there are some cases where this approach is insufficient, so the QualityScore procedure takes the following additional steps to normalize both ASTs:

First, we identify any `value`s present in the hint AST that are not present in the student's original AST and normalize these to an empty value. This allows the gold standard hint "create a variable named *somethingSpecific*" to match the hint "create a variable" or "create a variable named *somethingElse*". This allows hint generation algorithms to match gold standard hints without having to exactly match natural language variable names. For the iSnap dataset, we included numeric values in this procedure (because no assignment solution required numeric values), but for the ITAP dataset we excluded numeric values from this process (because many assignments required specific numeric values). Second, we "clean up" the ASTs, removing nodes which may have lost meaning (e.g. a code block from which all children have been removed), as well as nodes which were automatically added when their parents were added (e.g. the default input values for newly added Snap blocks). This allows an automatically generated hint to match a gold standard hint without matching all the syntactic intricacies of compiler-generated ASTs.


## Grammar Files

The snap-grammar.json and python-grammar.json files may be useful in generating hints. They define the valid format for ASTs in the given languages. Note that these files are generated automatically based on the corpus of data in these datasets, not using a priori knowledge of the programming languages themselves. As such, they define constraints for the ASTs that *have been seen* in this dataset, with the assumption that a valid hint will also follow these constraints. The grammars are represented as a JSON object with the following fields:

### root
An array of the permitted `node_types` or `categories` for the root node.

### node_types
A map of valid node types, with the key corresponding to the type itself, and the value being a JSON object with the following fields:
* `type`: Whether this node should have a `fixed` number of children or a `flexible` number of children.
* `count`: If the type is `fixed`, the number of children this node should have.
* `1`...`n`: If the type is `fixed`, each entry is an array of the permitted `node_types` or `categories`  for the `i`th child.
* `permitted_children`: If the type if `flexible`, an array of the permitted `node_types` or `categories`  for the children of this node.

**Note**: Types for children will either be a lowercase type, corresponding to one of the `node_types` *or* one of the uppercase `categories`.

### categories
A map of defined type categories, with keys corresponding to the name of the category and values being a list of types which fall into that category. This makes it more feasible to list the valid child types for a given node, since many nodes can have whole categories of types as children. For example, in Snap, the `REPORTER` category includes all built-in procedures with a return value. **Note** that some types may fall into multiple categories.

### special_types
A list of all types which do not fall into a category.

## Algorithms Directory (Generated Hint)

This directory contains hint generated by a number of data-driven hint generation algorithms using the training data in the dataset. Each directory corresponds to an algorithm and contains subdirectories for each assignments. Each of these contains a .json file for each hint generated, with the format `hintRequestID_hintNumber.json`. The .json file format matches those in the training and test datasets.

The algorithms evaluated include:

1. Target Recognition - Edit Recommendation (TRER): An algorithm by Zimmerman and Rupakheti [5] that uses pq-Gram distance to recommend edits towards the nearest correct solution.
2. Continuous Hint Factory (CHF_with_past, CHF_without_past): Two variants of an algorithm by Paaßen et al. [6], one which compares whole student traces (with past) and one which just compares snapshots (without past).
3. Next Step of Nearest Learner Solution (NSNLS): A hint policy based on [7], which suggests the next step of the closest trace in the dataset.
4. Contextual Tree Decomposition (CTD): An algorithm by Price et al. [8] that decomposes ASTs down into smaller subtrees and looks for matches of these subtrees in the dataset.
5. SourceCheck: An algorithm by Price et al. [9] that recommends edits towards the nearest correct solution.
6. ITAP: An algorithm by Rivers and Koedinger [3], only available on the ITAP dataset. These are the historical hints actually offered when the dataset was collected.

## Programming Problems

This section contains descriptions of the programming problems included in these datasets.

### iSnap Datasets

For a complete description with pictures, please see [this document](https://docs.google.com/document/d/1YxYPsagMO7CUFxV7J2e3cL1i39Kn6V6oF93UqolxkVI/pub).

#### Squiral (squiralHW)

Students completed this activity for homework and had a week to complete it. The goal is to define a procedure to draw a square-sprial pattern. Common solutions are ~10 lines of code.

**Instructions**: In this activity you will build a block, in SNAP, that makes your sprite draw a squiral like the one below:

![Squairal Image](https://lh3.googleusercontent.com/u_oZCblVlwxxOnDil0jWeA3Nlqlgip3Y3wZdY2-Z-YJAY1-3nEIgnGUkVGUX-bhKMLw8KSptIM3ib52iBIJteFrmoGuxETgYV_X8YPElIbO_GpLcYIADvMo7kC7V-TtfQQlRfxPAAntxUidyUQ)

Give the block an argument that allows the user to set the number of times the sprite completes a full rotation around the center. (The picture has at least 5 rotations).

#### Guessing Game (guess1Lab)

Students completed this activity in a lab with an undergraduate teaching assistants available to help them. The goal is to create a "guessing game" where the computer comes up with a random number and the player must guess it. Common solutions are ~13 lines of code.

**Instructions**:
1. The computer chooses a random number between 1 and 10 and continuously asks the user to guess the number until they guess correctly.
2. Make sure that your program contains the following:
  * Welcome the user to the game
  * Ask the user's name
  * Welcome the user by name
  * Tell the user if their guess was too high, too low, or correct

### ITAP Dataset

#### firstAndLast
Given a string, s, return the combination of the first letter and the last letter of the string. You can assume that s is at least two characters long.

*Example Solution*:
```python
    def firstAndLast(s):
        return s[0] + s[len(s) - 1]
```

####  helloWorld
Write a function, hello_world, which takes no parameters and returns the string "Hello World!".

*Example Solution*:
```python
    def helloWorld():
        return "Hello World!"
```

#### oneToN
Given a number n, return a string that contains the numbers from 1 to n. (So 5 results in "12345")

*Example Solutions*:
```python
    def oneToN(n):
        return "".join([str(i) for i in range(1, n + 1)])

    def oneToN(n):
        text = ""
        for i in range(1, (n + 1)):
	        text += str(i)
        return text
```

#### isPunctuation
Given a one-character string, write a function, is_punctuation, which returns whether that character is a punctuation mark. Hint: you can do this by importing the string module and using the built-in punctuation value.

*Example Solution*:
```python
    import string
    def isPunctuation(c):
        return c in string.punctuation
```

#### kthDigit
Given a number, x, return the kth digit (from the back) of x. In the number 1234, the 1st digit is 4, the 2nd is 3, etc. You can assume that x is positive. Note that // is truncating integer division in python.

*Example Solutions*:
```python
    def kthDigit(x, k):
        return (x // (10 ** (k - 1))) % 10

    def kthDigit(x, k):
        return (x % (10 ** k)) // (10 ** (k - 1))
```

## References

[1] Price, T. W., R. Zhi, Y. Dong, N. Lytle and T. Barnes. "The Impact of Data Quantity and Source on the Quality of Data-driven Hints for Programming." International Conference on Artificial Intelligence in Education. 2018.

[2] Price, T. W., Y. Dong and D. Lipovac. "iSnap: Towards Intelligent Tutoring in Novice Programming Environments." ACM Special Interest Group on Computer Science Education (SIGCSE). 2017.

[3] Rivers, K. and K. R. Koedinger, "Data-Driven Hint Generation in Vast Solution Spaces: a Self-Improving Python Programming Tutor," International Journal of Artificial Intelligence i  Education, vol. 27, no. 1, pp. 37–64, 2017.

[4] Rivers, K., E. Harpstead, and K. Koedinger, "Learning Curve Analysis for Programming: Which Concepts do Students Struggle With?," in Proceedings of the International Computing Education Research Conference, 2016, pp. 143–151.

[5] Zimmerman, K. and C. R. Rupakheti, “An Automated Framework for Recommending Program Elements to Novices,” in Proceedings of the International Conference on Automated Software Engineering, 2015.

[6] Paaßen, B., B. Hammer, T. W. Price, T. Barnes, S. Gross, and N. Pinkwart, “The Continuous Hint Factory -Providing Hints in Vast and Sparsely Populated Edit Distance Spaces,” J. Educ. Data Min., pp. 1–50, 2018.

[7] Gross, S., B. Mokbel, B. Hammer, and N. Pinkwart, “How to Select an Example? A Comparison of Selection Strategies in Example-Based Learning,” in Proceedings of the International Conference on Intelligent Tutoring Systems, 2014, pp. 340–347.

[8] Price, T. W., Y. Dong, and T. Barnes, “Generating Data-driven Hints for Open-ended Programming,” in Proceedings of the International Conference on Educational Data Mining, 2016.

[9] Price, T. W., R. Zhi, and T. Barnes, “Evaluation of a Data-driven Feedback Algorithm for Open-ended Programming,” in Proceedings of the International Conference on Educational Data Mining, 2017.