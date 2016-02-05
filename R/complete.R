library("plyr")
library("ggplot2")

se <- function(x) sqrt(var(x)/length(x))

loadData <- function() {
  rm(list=ls())
  maxTest <<- 8;
  complete <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/complete.csv")
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  complete$grade <<- rowSums(complete[,tests]) / 9
  combined <<- ddply(complete, .(policy, slice), summarize, 
                     gradeMean = mean(grade), gradeSE = se(grade), 
                     stepsMean = mean(steps), stepsSE = se(steps), 
                     studentStepsMean = mean(studentSteps), studentStepsSE = se(studentSteps), 
                     hashCount = length(unique(hash)))
}

idealSolutions <- function() {
  solutions <- complete[complete$grade==1,c("policy", "hash")]
  policies <- sapply(unique(complete$policy), as.character)
  plot(solutions)
  sapply(policies, function (policy) {
    length(unique(complete[complete$policy==policy,]$hash))
  })
}

plotHash <- function() {
  ggplot(combined, aes(x=slice, y=hashCount, colour=policy)) + 
    geom_line() +
    geom_point()
}


plotGrade <- function() {
  ggplot(combined, aes(x=slice, y=gradeMean, colour=policy)) + 
    geom_errorbar(aes(ymin=gradeMean-gradeSE, ymax=gradeMean+gradeSE), width=.4) +
    geom_line() +
    geom_point()
}

plotSteps <- function() {
  ggplot(combined, aes(x=slice, y=stepsMean, colour=policy)) + 
    geom_errorbar(aes(ymin=stepsMean-stepsSE, ymax=stepsMean+stepsSE), width=.4) +
    geom_line() +
    geom_point()  
}

plotStudentSteps <- function() {
  ggplot(combined[combined$policy=="Hint All",], aes(x=slice, y=studentStepsMean)) + 
    geom_errorbar(aes(ymin=studentStepsMean-studentStepsSE, ymax=studentStepsMean+studentStepsSE), width=.4) +
    geom_line() +
    geom_point()  
}

