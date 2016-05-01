library("plyr")
library("ggplot2")

se <- function(x) sqrt(var(x)/length(x))

loadData <- function() {
  rm(list = ls())
  
  prediction <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/prediction-p.csv")
  prediction$percActions <<- prediction$predicted / prediction$actions
  prediction$percHints <<- prediction$predicted / prediction$hints
  
  predictionSummary <<- ddply(prediction, .(policy), summarize, 
                              actionsMean = mean(percActions), actionsSD = sd(percActions), 
                              hintsMean = mean(percHints), hintsSD = sd(percHints),
                              corActions = cor(percActions, grade), corHints = cor(percHints, grade))
  
  policies <<- sapply(unique(prediction$policy), as.character)
  students <<- sapply(unique(prediction$student), as.character)
  
  distance <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/distance-p.csv")
  distance$mNodeDis <<- distance$nodeDis / distance$totalAction
  distance$mHintDis <<- distance$hintDis / distance$totalAction
  distance$percCloser <<- distance$closer / distance$totalHints
  distance$percFarther <<- distance$farther / distance$totalHints
  #distance$percDel <<- distance$deletions / distance$totalHints
  
  distanceSummary <<- ddply(distance, .(target, normalized, policy), summarize, 
                            percCloserMean = mean(percCloser), percCloserSD = sd(percCloser),
                            percFartherMean = mean(percFarther), percFartherSD = sd(percFarther),
                            #percDelMean = mean(percDel), percDelSD = sd(percDel),
                            mNodeDisMean = mean(mNodeDis), mNodeDisSD = sd(mNodeDis),
                            mHintDisMean = mean(mHintDis), mHintDisSD = sd(mHintDis),
                            corCloser = cor(percCloser, grade), corMHintDis = cor(mHintDis, grade))
}

improvement <- function(a, b) {
  mean(sapply(students, function(student) {
    d <- distance[distance$student==student & distance$target=="final" & distance$normalized == TRUE,]
    mean(d[d$policy==a,]$percCloser) > mean(d[d$policy==b,]$percCloser)
  }))
}

plotCloser <- function() {
  d <- distance[distance$target=="final" & distance$normalized == TRUE,]
  ggplot(d, aes(x = percCloser, color=policy)) + geom_density()
}

testCloser <- function() {
  d <- distance[distance$target=="final" & distance$normalized == TRUE,]
  
  print(summary(aov(percCloser ~ policy, d)))
  print(summary(aov(percCloser ~ policy, d[d$policy != "Direct Ideal" & d$policy != "Student Next",])))
  
  hintAll <- d[d$policy=="Hint All",]$percCloser
  hintExemplar <- d[d$policy=="Hint Exemplar",]$percCloser
  directIdeal <- d[d$policy=="Direct Ideal",]$percCloser
  directStudent <- d[d$policy=="Direct Student",]$percCloser
  
  
  #print(t.test(hintExemplar, directStudent, paired = TRUE))
  #print(t.test(hintExemplar, directIdeal, paired = TRUE))
  #print(t.test(hintAll, directIdeal, paired = TRUE))
  print(t.test(hintAll, directStudent, paired = TRUE))
  print(t.test(hintAll, directIdeal, paired = TRUE))
  print(t.test(hintAll, hintExemplar, paired = TRUE))
}

plotFarther <- function() {
  d <- distance[distance$target=="final" & distance$normalized == TRUE,]
  ggplot(d, aes(x = percFarther, color=policy)) + geom_density()
}