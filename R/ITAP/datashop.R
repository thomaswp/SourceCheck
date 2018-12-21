
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

metadata <- data.frame(Property=c("Version", "AreEventsOrdered", "IsEventOrderingConsistent", "CodeStateRepresentation"), c(3,T,T,"Table"))

write.csv(attempts, "data/DataChallenge/MainTable.csv", row.names = F)
write.csv(metadata, "data/DataChallenge/DatasetMetadata.csv", row.names = F)
write.csv(codeStates, "data/DataChallenge/CodeStates/CodeState.csv", row.names = F)

last <- function(x) tail(x, 1)
lastAttempts <- ddply(attempts, c("`Anon Student Id`", "`Problem Name`"), summarize, correct=last(pCorrect==1), pCorrect=last(pCorrect), code=last(Input))

byProblem <- ddply(attempts, c("ProblemID"), summarize, n=length(unique(SubjectID)))
byProblem <- byProblem[order(-byProblem$n),]
plot(byProblem$n)
mean(byProblem$n > 20)
