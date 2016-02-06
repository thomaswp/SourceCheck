library("plyr")
library("ggplot2")

se <- function(x) sqrt(var(x)/length(x))

loadData <- function() {
  rm(list = ls())
  
  prediction <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/prediction.csv")
  prediction$percActions <<- prediction$predicted / prediction$actions
  prediction$percHints <<- prediction$predicted / prediction$hints
  
  predictionSummary <<- ddply(prediction, .(policy), summarize, 
                              actionsMean = mean(percActions), actionsSD = sd(percActions), 
                              hintsMean = mean(percHints), hintsSD = sd(percHints),
                              corActions = cor(percActions, grade), corHints = cor(percHints, grade))
  
  policies <<- sapply(unique(prediction$policy), as.character)
  students <<- sapply(unique(prediction$student), as.character)
  
  distance <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/distance.csv")
  distance$mNodeDis <<- distance$nodeDis / distance$totalAction
  distance$mHintDis <<- distance$hintDis / distance$totalAction
  distance$percCloser <<- distance$closer / distance$totalHints
  
  distanceSummary <<- ddply(distance, .(target, normalized, policy), summarize, 
                            percCloserMean = mean(percCloser), percCloserSD = sd(percCloser),
                            mNodeDisMean = mean(mNodeDis), mNodeDisSD = sd(mNodeDis),
                            mHintDisMean = mean(mHintDis), mHintDisSD = sd(mHintDis),
                            corCloser = cor(percCloser, grade), corMHintDis = cor(mHintDis, grade))
}

improvement <- function() {
  mean(sapply(students, function(student) {
    d <- distance[distance$student==student & distance$target=="final" & distance$normalized == TRUE,]
    mean(d[d$policy=="Hint Exemplar",]$percCloser) > mean(d[d$policy=="Direct Ideal",]$percCloser)
  }))
}