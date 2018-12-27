# The CSEDM 2019 Data Challenge

As part of the 2nd [Educational Data Mining in Computer Science Education](https://sites.google.com/asu.edu/csedm-ws-lak-2019/) (CSEDM) Workshop at [LAK 2019](https://lak19.solaresearch.org), we are releasing a **Data Challenge**. The goal of this challenge is to bring researchers together to tackle a common data mining task that is specific to CS Education. This year, we are focusing on the task of modeling students' programming knowledge in order to predict their performance on future tasks.
The rest of this document contains a summary of the task itself and documentation of the dataset used in the challenge.

For the most up-to-date information on the challenge and the workshop, see the workshop's [call for papers](https://sites.google.com/asu.edu/csedm-ws-lak-2019/call-for-papers).

For any questions on the Data Challenge, please contact Thomas Price at twprice@ncsu.edu.

## Challenge Summary

The goal of this Data Challenge is to use previous students' programming process data to predict whether future students will succeed at a given programming task. This is a central challenge of student modeling, often called Knowledge Tracing.


## The Data

The dataset used in the challenge comes from a study of novice Python programmers working with the ITAP intelligent tutoring system []. For more information on the original experiment, see []. There are 89 total students represented, and they worked on 38 problems over time. The study lasted over 7 weeks. The students could attempt the problems in any order, though there was a default order. Students could attempt the problem any number of times, receiving feedback from test cases each time, and they could also request hints from ITAP (though access was limited for students, depending on the week and their experimental condition). The dataset itself contains a record for each attempt and hint request that students made while working.

The original data is available at the PSLC Datashop
[] [here](https://pslcdatashop.web.cmu.edu/DatasetInfo?datasetId=1798). The data used for the challenge has been processed to make it easier to work with. The data is organized using the ProgSnap 2 format for programming process data [], with some additional files included specifically for this challenge.

The dataset is organized as follows:

DataChallenge
    DatasetMetadata.csv
    MainTable.csv
    CodeStates
        CodeState.csv
    Predict.csv
    CV
        Fold0
            Test.csv
            Training.csv
        Fold1
        ...
        Fold9

**DatasetMetadata.csv**: Required metadata information for the ProgSnap 2 format.

**MainTable.csv**: Contains all programming process data. Each row of the table represents an event: one attempt or hint request, made by one student on a given problem. The following columns are defined for each row:
* EventType: Either `Submit` if this was a submission or `X-HintRequest` if the student requested a hint.
* EventID: A Unique ID for this event.
* Order: An integer indicating the chronological order of this event, relative to each other event in the table. This is necessary to order events that occurred at the same timestamp.
* SubjectID: A unique ID for each participant in the study.
* ToolInstances: The tutoring system and programming language being used. In this case, it is always ITAP and Python.
* CodeStateID: An ID for the students code at the time of the event. This corresponds to one row in the CodeStates/CodeState.csv file, which contains the text of the code.
* ServerTime: The time of the event.
* ProblemID: An ID for the problem being worked on by the student.
* Correct: Whether the student's  code was correct at this time according to the test cases.

**CodeStates/CodeState.csv**: This file contains a mapping from each CodeStateID in the MainTable to the source code it represents.

**Predict.csv**: While the MainTable.csv table includes *all* events in the dataset, the Predict.csv table includes one row for each prediction to be made. Specifically, there is one row for each problem/student pair. The goal of the challenge is to predict, at each new problem, whether a given student will succeed on the *first attempt* at the problem, which is defined as getting it correct without requesting a hint. There are a number of additional outcomes we may wish to predict as well, such as home *many* attempts the student will take, whether they will ever get it correct and whether they will request a hint. Therefore each row of the table includes the following columns:
* SubjectID: An ID for the student attempting the problem.
* ProblemID: An ID for the problem being attempted.
* StartOrder: The first "Order" value in the MainTable corresponding to the student's attempt at this problem. When making a prediction, an algorithm may use any history and information from the student *up until* the event row with this Order value. For example, if this value is 1354, the algorithm can base its prediction on the student's performance on problems on *previous* problems, with Order values < 1354.
* FirstCorrect: Whether the student got this problem correct on their first attempt. This is the primary value to be predicted in this challenge.
* EverCorrect: Whether the student ever got this problem correct.
* UsedHint: Whether the student used a hint on this problem.
* Attempts: The total number of attempts the student made at this problem.

**Note**: The challege only includes predictions

helloWorld doubleX raiseToPower convertToDegrees leftoverCandy intToFloat findRoot howManyEggCartons kthDigit nearestBusStop hasTwoDigits overNineThousand canDrinkAlcohol isPunctuation oneToN backwardsCombine isEvenPositiveInt firstAndLast singlePigLatin