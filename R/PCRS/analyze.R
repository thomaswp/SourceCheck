

library(plyr)
library(readr)


loadData <- function() {
  
  # You must replace the string: "([0-9]+,[0-9]+,[0-9]+,2017-)
  # with the string: "\n$1
  # code.states <- read.csv("data/code-states.csv")
  code.states <- read.csv("data/code-states-corrected.csv")
  problems <- read.csv("data/problems.csv")
  
  byStudent <- ddply(code.states, c("user_id", "problem_id"), summarize, n=length(status), pCorrect=mean(status=="Pass"))
  byProblem <- ddply(byStudent, "problem_id", summarize, nStudents=length(n), meanCorrect=mean(pCorrect), meanAttempts=mean(n), pFirstCorrect=mean(n==1 & pCorrect==1))

  byProblem <- merge(byProblem, problems, all.x = T, all.y = F)
  
  plot(byProblem$nStudents, col=byProblem$type)
  plot(sort(byProblem$nStudents))
  byProblem$problem_id[byProblem$nStudents < 400]
}
