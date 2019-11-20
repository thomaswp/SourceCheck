
library(plyr)
library(reshape2)
library(readr)

first <- function(x) head(x, 1)
last <- function(x) tail(x, 1)

# itap <- read_delim("data/itap_pslc.tsv", "\t", escape_double = FALSE, trim_ws = TRUE)
# There's a bug in the original data where some program code didn't get properly terminated w/ a quote
# You have to replace theis string: ((CORRECT|HINT)\t\t\t"([^"\t]+))\t
# with this: $1"\t
itap <- read_delim("data/itap_pslc_corrected.tsv", "\t", escape_double = FALSE, trim_ws = TRUE)

hintRows <- itap[itap$`Student Response Type` == "HINT_REQUEST" & !is.na(itap$Input) & nchar(itap$Input) < 400,]

hints <- ddply(hintRows, c("`Anon Student Id`", "`Problem Name`", "`Time`", "`Input`", "`Feedback Text`"), summarize, id=first(`Transaction Id`), count=length(Time))
names(hints) <- c("sid", "problem", "time", "code", "hint", "id", "count")
allRequests <- ddply(hints, c("sid", "problem", "code", "hint"), colwise(first))

write.csv(allRequests, "data/all-requests.csv")

# --- Now process them in python ---

processed <- read_csv("data/processed-requests.csv")
filtered <- allRequests[processed$parsable == "True" & (is.na(processed$correct) | processed$correct == "False"),]

studentsPerProblem <- table(ddply(filtered, c("sid", "problem"), summarize, count=length(problem))$problem)

set.seed(1234)
selected <- ddply(filtered, c("sid", "problem"), summarize, i=sample(1:length(time), min(2, length(time))), time=time[i], code=code[i], hint=hint[i], id=id[i])
selected <- ddply(selected, c("problem", "code"), colwise(first))

requestsPerProblem <- table(selected$problem)
problems <- names(requestsPerProblem[requestsPerProblem >= 8])
# problems <- problems[problems != "helloWorld"]

selected <- selected[selected$problem %in% problems,]

# We remove whitespace from the code and merge again to get ride of duplicates
# the differ only by whitespace. We do this now (instead of before selecting problems)
# because I already settled on our problems before I wrote this
selected$codeNoWS <- gsub("\\s|\\\\t|\\\\n", "", selected$code)
selected <- ddply(selected, c("problem", "codeNoWS"), colwise(first))

write.csv(selected, "data/selected-requests.csv", row.names = F)

writeSQL <- function(rows, users, path) {
  sink(path)
  for (user in users) {
    for (i in 1:nrow(rows)) {
      line = sprintf("INSERT INTO `handmade_hints` (`userID`, `rowID`, `trueAssignmentID`) VALUES ('%s', '%s', '%s');\n",
                    user, rows[i, "id"], rows[i,"problem"])
      cat(line)
    }
  }
  sink()
}
writeSQL(selected, c("twprice", "vmcatete", "nalytle"), "data/selected-requests.sql")

history <- ddply(itap, c("`Anon Student Id`", "`Problem Name`", "`Time`", "`Input`", "`Feedback Text`"), summarize, id=first(`Transaction Id`), count=length(Time), row=first(`Row`))
names(history) <- c("sid", "problem", "time", "code", "hint", "id", "count", "row")
history <- history[history$problem %in% problems,]
write.csv(history, "data/history.csv", na="NULL")

set.seed(1234)
testReqs <- filtered[!(filtered$id %in% selected$id) & filtered$problem %in% problems,]
testReqs <- ddply(testReqs, "problem", summarize, i=sample(1:length(time), min(3, length(time))), sid=sid[i], code=code[i], hint=hint[i], id=id[i])
write.csv(testReqs[,-2], "data/test-requests.csv", row.names = F)
writeSQL(testReqs, c("twprice", "vmcatete", "nalytle"), "data/test-requests.sql")


##### Data Challenge

attempts <- ddply(itap, c("`Anon Student Id`", "`Time`", "`Duration (sec)`", "`Student Response Type`", "`Problem Name`", "Input"), 
                  summarize, n=length(`Anon Student Id`), dups=n/length(unique(`KC (Tokens)`)), 
                  pCorrect=mean(Outcome=="CORRECT"), EventID=head(`Transaction Id`, 1))
attempts <- attempts[order(attempts$Time),]
attempts$order <- 1:nrow(attempts)

codeStates <- unique(attempts$Input)
codeStates <- data.frame(CodeStateID=1:length(codeStates), Code=codeStates)

attempts <- merge(attempts, codeStates, by.x="Input", by.y="Code")
attempts <- attempts[order(attempts$order),]

library(stringr)
codeStates$Code <- str_replace_all(codeStates$Code, "\\\\n", "\n")
codeStates$Code <- str_replace_all(codeStates$Code, "\\\\t", "\t")


attempts$EventType <- ifelse(attempts$`Student Response Type` == "ATTEMPT", "Submit", "X-HintRequest")
attempts$ToolInstances <- "ITAP; Python"

attempts <- attempts[,c(13, 10, 11, 2, 14, 12, 3, 6, 9)]
names(attempts) <- c("EventType", "EventID", "Order", "SubjectID", "ToolInstances", "CodeStateID", "ServerTimestamp", "ProblemID", "Correct")

attempts$Correct <- attempts$Correct == 1
test <- strptime(attempts$ServerTimestamp, "%Y-%m-%d %H:%M:%S")
attempts$ServerTimestamp <- strftime(test, "%Y-%m-%dT%H:%M:%S")
attempts <- attempts[order(attempts$SubjectID, attempts$Order),]

metadata <- data.frame(Property=c("Version", "AreEventsOrdered", "IsEventOrderingConsistent", "CodeStateRepresentation"), Value=c(3,T,T,"Table"))

##### Add manual testing for correctness

attemptsWithCode <- merge(attempts, codeStates)
write.csv(attemptsWithCode, "data/attempts.csv", row.names = F)

# Do Python magic

attempts_tests <- read.csv("~/GitHub/SnapHints/R/ITAP/data/attempts_tests.csv")
attempts_tests$Test[attempts_tests$Test == ''] <- NA
attempts_tests$Test <- attempts_tests$Test == 'True'
byProblemTest <- ddply(attempts_tests, "ProblemID", summarize, n=length(unique(SubjectID)), pCorrect=mean(Correct), pTest=mean(Test, na.rm=T), cor=cor(Correct,Test))
plot(byProblemTest$pCorrect, byProblemTest$pTest)
View(byProblemTest)

attempts_tests <- attempts_tests[order(attempts_tests$SubjectID, attempts_tests$Order),]
mean(as.character(attempts$EventID) == attempts_tests$EventID)
attempts$Correct <- attempts_tests$Test

### Write files


createDir <- function(mainDir, subDir) ifelse(!dir.exists(file.path(mainDir, subDir)), dir.create(file.path(mainDir, subDir)), FALSE)

createDir("data", "DataChallenge")
createDir("data/DataChallenge", "CodeStates")

write.csv(attempts, "data/DataChallenge/MainTable.csv", row.names = F)
write.csv(metadata, "data/DataChallenge/DatasetMetadata.csv", row.names = F)
write.csv(codeStates, "data/DataChallenge/CodeStates/CodeState.csv", row.names = F)



##### Problem Stats

byProblem <- ddply(attempts, c("ProblemID"), summarize, n=length(unique(SubjectID)))
byProblem <- byProblem[order(-byProblem$n),]
plot(byProblem$n)
sum(byProblem$n >= 20)
testProblems <- byProblem$ProblemID[byProblem$n >= 20]

##### Cross Validation

userIDs <- unique(attempts$SubjectID)
splits <- data.frame(SubjectID=userIDs)
set.seed(1234)
nSplits <- 10
for (i in 1:4) {
  splits[,paste0("Split", i)] <- sample(1:length(userIDs), replace=F) %% nSplits
}
# write.csv(splits, "data/DataChallenge/Splits.csv", row.names = F)
createDir("data/DataChallenge", "CV")

predict <- ddply(attempts, c("SubjectID", "ProblemID"), summarize, 
                 StartOrder=first(Order),
                 FirstCorrect=first(Correct), 
                 EverCorrect=sum(Correct)>0,
                 UsedHint=sum(EventType=="X-HintRequest")>0,
                 Attempts=min(first(which(Correct)), sum(EventType=="Submit"))
)
predict <- predict[order(predict$SubjectID, predict$StartOrder),]
predict <- predict[predict $ProblemID %in% testProblems,]
write.csv(predict, "data/DataChallenge/Predict.csv", row.names = F)

for (fold in 0:(nSplits-1)) {
  trainingIDs <- splits$SubjectID[splits$Split1 != fold]
  training <- predict[predict$SubjectID %in% trainingIDs,]
  test <- predict[!(predict$SubjectID %in% trainingIDs),]
  createDir("data/DataChallenge/CV", paste0("Fold", fold))
  write.csv(training, paste0("data/DataChallenge/CV/Fold", fold, "/Training.csv"), row.names = F)
  write.csv(test, paste0("data/DataChallenge/CV/Fold", fold, "/Test.csv"), row.names = F)
}

# Second test dataset
createDir("data/DataChallenge", "CV2")
for (fold in 0:(nSplits-1)) {
  trainingIDs <- splits$SubjectID[splits$Split2 != fold]
  training <- predict[predict$SubjectID %in% trainingIDs,]
  test <- predict[!(predict$SubjectID %in% trainingIDs),]
  createDir("data/DataChallenge/CV2", paste0("Fold", fold))
  write.csv(training, paste0("data/DataChallenge/CV2/Fold", fold, "/Training.csv"), row.names = F)
  write.csv(test, paste0("data/DataChallenge/CV2/Fold", fold, "/Test.csv"), row.names = F)
}

