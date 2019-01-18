

library(plyr)
library(readr)


loadData <- function() {
  
  # You must replace the string: "([0-9]+,[0-9]+,[0-9]+,2017-)
  # with the string: "\n$1
  code.states <- read.csv("~/GitHub/SnapHints/R/PCRS/data/code-states.csv")
  # code.states <- read.csv("~/GitHub/SnapHints/R/PCRS/data/code-states-corrected.csv")
  
  byStudent <- ddply(code.states, c("user_id", "problem_id"), summarize, n=length(status), pCorrect=mean(status=="Pass"))
  byProblem <- ddply(byStudent, "problem_id", summarize, nStudents=length(n), meanCorrect=mean(pCorrect))

}
