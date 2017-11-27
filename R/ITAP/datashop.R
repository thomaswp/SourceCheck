
library(plyr)
library(reshape2)
library(readr)

first <- function(x) head(x, 1)
last <- function(x) tail(x, 1)

itap <- read_delim("~/GitHub/SnapHints/R/ITAP/data/itap_pslc.tsv", "\t", escape_double = FALSE, trim_ws = TRUE)
hintRows <- itap[itap$`Student Response Type` == "HINT_REQUEST" & !is.na(itap$Input) & nchar(itap$Input) < 400,]

hints <- ddply(hintRows, c("`Anon Student Id`", "`Problem Name`", "`Time`", "`Input`", "`Feedback Text`"), summarize, id=first(`Transaction Id`), count=length(Time))
names(hints) <- c("sid", "problem", "time", "code", "hint", "id", "count")
dedup <- ddply(hints, c("sid", "problem", "code", "hint"), colwise(first))

students <- ddply(hints, c("sid", "problem"), summarize, count=length(problem))

# table(hints$problem)

studentsPerProblem <- table(students$problem)
problems <- names(studentsPerProblem[studentsPerProblem > 6])
problems <- problems[problems != "helloWorld"]

set.seed(1234)
selected <- ddply(dedup[dedup$problem %in% problems,], c("sid", "problem"), summarize, i=sample(1:length(time), min(2, length(time))), time=time[i], code=code[i], hint=hint[i], id=id[i])
selected <- ddply(selected, c("problem", "code"), colwise(first))

set.seed(1234)
selected <- ddply(dedup[dedup$problem %in% problems,], c("sid", "problem"), colwise(last))

attempts <- ddply(itap[itap$`Student Response Type` == "ATTEMPT",], 
                  c("`Anon Student Id`", "`Problem Name`", "`Time`", "`Input`"), 
                  summarize, count=length(Time), correct=sum(Outcome=="CORRECT"))


attempts[attempts$`Problem Name` == "convertToDegrees" & attempts$correct == attempts$count,]$Input
table(attempts[attempts$`Problem Name` == "helloWorld" & attempts$correct == attempts$count,]$Input)
table(attempts[attempts$`Problem Name` == "howManyEggCartons" & attempts$correct == attempts$count,]$Input)