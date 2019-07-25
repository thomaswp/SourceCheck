

library(plyr)
library(readr)
library(stringr)


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
  
  printSolutions(57, "Fail", 5)
}

printSolutions <- function (id, status, n) {
  p57 <- code.states[code.states$problem_id==id,]
  p57$code <- as.character(p57$code)
  p57$codeW <- str_replace_all(p57$code, "[\\s|\\n]*", "")
  p57.correct <- table(p57$codeW[p57$status==status])
  p57.correct <- p57.correct[order(p57.correct, decreasing = T)]
  for (i in 1:n) {
    print(paste(i, p57.correct[i], "/", sum(p57.correct), "=", p57.correct[i]/sum(p57.correct), "%"))
    cat(head(p57$code[p57$codeW == names(p57.correct)[i]], 1))
  }
}
