
library(plyr)
library(reshape2)
library(readr)

itap <- read_delim("~/GitHub/SnapHints/R/ITAP/data/itap_pslc.tsv", "\t", escape_double = FALSE, trim_ws = TRUE)
hintRows <- itap[itap$`Student Response Type` == "HINT_REQUEST",]

hints <- ddply(hintRows, c("`Anon Student Id`", "`Problem Name`", "`Time`", "`Input`", "`Feedback Text`"), summarize, count=length(Time))
names(hints) <- c("sid", "problem", "time", "code", "hint")

students <- ddply(hints, c("sid", "problem"), summarize, count=length(problem))

table(hints$problem)

studentsPerProblem <- table(students$problem)
names(studentsPerProblem)[studentsPerProblem > 6]


attempts <- ddply(itap[itap$`Student Response Type` == "ATTEMPT",], 
                  c("`Anon Student Id`", "`Problem Name`", "`Time`", "`Input`"), 
                  summarize, count=length(Time), correct=sum(Outcome=="CORRECT"))


attempts[attempts$`Problem Name` == "convertToDegrees" & attempts$correct == attempts$count,]$Input
table(attempts[attempts$`Problem Name` == "helloWorld" & attempts$correct == attempts$count,]$Input)
table(attempts[attempts$`Problem Name` == "howManyEggCartons" & attempts$correct == attempts$count,]$Input)