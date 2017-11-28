
library(plyr)
library(reshape2)
library(readr)

first <- function(x) head(x, 1)
last <- function(x) tail(x, 1)

itap <- read_delim("~/GitHub/SnapHints/R/ITAP/data/itap_pslc.tsv", "\t", escape_double = FALSE, trim_ws = TRUE)
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
write.csv(selected, "data/selected-requests.csv", row.names = F)

sink("data/hints.sql")
for (i in 1:nrow(selected)) {
  line = sprintf("INSERT INTO `handmade_hints` (`userID`, `rowID`, `trueAssignmentID`) VALUES ('%s', '%s', '%s');\n",
                "ITAP", selected[i, "id"], selected[i,"problem"])
  cat(line)
}
sink()
