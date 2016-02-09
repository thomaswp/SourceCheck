library("plyr")
library("ggplot2")
library("reshape2")

loadData <- function() {
  rm(list=ls())
  
  maxTest <<- 8;
  
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  percs <<- sapply(0:maxTest, function(i) paste("perc", i, sep=""))
  
  steps <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/solve2.csv")
  steps$avgHints <<- steps$hints / steps$actions
  
  students <<- sapply(unique(steps$student), as.character)
  
  agg <- function(v) median(v, na.rm=TRUE)
  combined <<- ddply(steps, .(limited, policy), colwise(agg, tests))
  
  asGood <<- ddply(steps, .(limited, policy), colwise(function(v) mean(is.na(v) | v > 0), tests))
  better <<- ddply(steps, .(limited, policy), colwise(function(v) mean(is.na(v) | v > 1), tests))
}

plotSteps <- function(limited) {
  data <- steps[steps$limited == limited & steps$policy != "Student Next",]
  agg <- function(v) median(v, na.rm=TRUE)
  data <- ddply(data, .(policy), colwise(agg, tests))
  data <- melt(data, id=c("policy"))
  ggplot(data, aes(policy, value, fill=variable)) +
    geom_bar(stat='identity') +
    scale_fill_grey()
}

plotBetter <- function(better, limited) {
  data <- better[better$limited == limited & better$policy != "Student Next",]
  data <- melt(data, id=c("policy", "limited"))
  ggplot(data, aes(variable, value, fill=policy)) +
    geom_bar(stat='identity', position='dodge') + 
    scale_fill_grey()
}