

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
  
  printSolutions(42, "Fail", 20)
  printSolutions(61, "Fail", 5)
  printSolutions(63, "Fail", 5)
  
  printSolutions(35, "Pass", 5)
  printSolutions(35, "Fail", 5)
}

printSolutions <- function (id, status, n) {
  p57 <- code.states[code.states$problem_id==id,]
  p57$code <- as.character(p57$code)
  p57$codeW <- str_replace_all(p57$code, "[\\s|\\n]*", "")
  p57.correct <- data.frame(codeW=unique(p57$codeW[p57$status==status]))
  p57.correct$users <- sapply(p57.correct$codeW, function(codeW) length(unique(p57$user_id[p57$codeW == codeW])))
  p57.correct$total <- sapply(p57.correct$codeW, function(codeW) sum(p57$codeW == codeW))
  p57.correct <- p57.correct[order(p57.correct$users, decreasing = T),]
  if (status == "Fail") {
    p57.correct$indent <- sapply(p57.correct$codeW, function(codeW) mean(p57$status[p57$codeW == codeW] == "Fail") < 1)
    p57.correct <- p57.correct[p57.correct$indent == F,]
  }
  
  nUsers <- length(unique(p57$user_id))
  
  for (i in 1:n) {
    print(paste(i, p57.correct$users[i], "/", nUsers, "=", p57.correct$users[i]*100/nUsers, "%"))
    cat(head(p57$code[p57$codeW == p57.correct$codeW[i]], 1))
  }
}
