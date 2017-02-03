source("../Hints Comparison/util.R")

library(ggplot2)
library(plyr)
library(depmixS4)

testHMM <- function() {
  events <- read.csv("../../data/csc200/fall2016/analysis/squiralHW/events.csv")
  
  attempts <- ddply(events, c("id"), summarize, obs=list(category))
  l <- c()
  for (i in 1:nrow(attempts)) {
    l[[i]] <- attempts$obs[i][[1]]
  }
  
  df <- data.frame(obs=l[[1]])
  
  states <- c("A", "B")
  symbols <- c("L", "R")
  hmm <- initHMM(states, symbols,
                 transProbs=matrix(c(.9,.1,.1,.9),2),
                 emissionProbs=matrix(c(.5,.51,.5,.49),2))
  obs <- sample(symbols, 800, replace=T)
  vt <- viterbiTraining(hmm, observation, 10)
  
  a = sample(c(rep("L",100),rep("R",300)))
  b = sample(c(rep("L",300),rep("R",100)))
  observation = c(a,b)
  vt <- viterbiTraining(hmm, observation, 10)
  
  
  a <- l[[1]]
  b <- l[[2]]
  ll <- c(a, b)
}
