library("plyr")
library("ggplot2")
library("scales")

se <- function(x) sqrt(var(x)/length(x))

loadData <- function() {
  rm(list=ls())
  maxTest <<- 8;
  complete <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/complete.csv")
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  complete$grade <<- rowSums(complete[,tests]) / 9
  combined <<- ddply(complete, .(policy, slice), summarize, 
                     gradeMean = mean(grade), gradeSE = se(grade), perfect=mean(grade==1),
                     stepsMean = mean(steps), stepsSE = se(steps), 
                     studentStepsMean = mean(studentSteps), studentStepsSE = se(studentSteps), 
                     hashCount = length(unique(hash)))
}

idealSolutions <- function(minGrade) {
  solutions <- complete[complete$grade >= minGrade,c("policy", "hash")]
  policies <- sapply(unique(solutions$policy), as.character)
  plot(solutions)
  sapply(policies, function (policy) {
    length(unique(solutions[solutions$policy==policy,]$hash))
  })
}

plotHash <- function() {
  ggplot(combined, aes(x=slice, y=hashCount, colour=policy)) + 
    labs(title="Unique Solutions", x="Slice", y="Unique Solutions", colour="Policy") +
    geom_line() +
    geom_point() +
    theme_bw()
}


plotGrade <- function() {
  ggplot(combined, aes(x=slice, y=gradeMean, color=policy)) + 
    labs(title="Final Solution Grade", x="Slice", y="Grade", fill="Policy") +
    geom_line() +
    geom_ribbon(aes(x=slice, ymin=gradeMean-gradeSE, ymax=gradeMean+gradeSE, fill=policy), color=NA, alpha=.3)+guides(colour=FALSE) +
    geom_point() +
    theme_bw() + 
    scale_y_continuous(limits=c(0.85, 1), label=percent)
}

plotPerfect <- function() {
  ggplot(combined, aes(x=slice, y=perfect, color=policy)) + 
    labs(title="Final Solution Grade", x="Slice", y="Grade", fill="Policy") +
    geom_line() +
    geom_point() +
    theme_bw() + 
    scale_y_continuous(label=percent)
}

plotSteps <- function() {
  ggplot(combined, aes(x=slice, y=stepsMean, colour=policy)) + 
    labs(title="Hints to Final Solution", x="Slice", y="Hints", fill="Policy") +
    geom_line() +
    geom_ribbon(aes(x=slice, ymin=stepsMean-stepsSE, ymax=stepsMean+stepsSE, fill=policy), color=NA, alpha=.3)+guides(colour=FALSE) +
    geom_point() +
    theme_bw()
}

plotStudentSteps <- function() {
  ggplot(combined[combined$policy=="Hint All",], aes(x=slice, y=studentStepsMean)) + 
    geom_errorbar(aes(ymin=studentStepsMean-studentStepsSE, ymax=studentStepsMean+studentStepsSE), width=.4) +
    geom_line() +
    geom_point()  
}

