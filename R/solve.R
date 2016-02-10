library("plyr")
library("ggplot2")
library("reshape2")
library("scales")

se <- function(x) sqrt(var(x)/length(x))

loadData <- function() {
  rm(list=ls())
  
  maxTest <<- 8;
  
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  percs <<- sapply(0:maxTest, function(i) paste("perc", i, sep=""))
  
  steps <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/solve1.csv")
  steps$policy <<- factor(steps$policy, levels = c("Hint All", "Hint Exemplar", "Direct Ideal", "Direct Student", "Student Next"))
  steps <<- steps[steps$round <= 5,]
  steps$avgHints <<- steps$hints / steps$actions
  
  students <<- sapply(unique(steps$student), as.character)
  
  agg <- function(v) median(v, na.rm=TRUE)
  combined <<- ddply(steps, .(limited, policy), colwise(agg, tests))
  
  asGood <<- ddply(steps, .(limited, policy, round), colwise(function(v) mean(is.na(v) | v > 0), tests))
  asGoodErr <<- ddply(asGood, .(limited, policy), colwise(se, tests))
  asGood <<- ddply(asGood, .(limited, policy), colwise(mean, tests))
  better <<- ddply(steps, .(limited, policy, round), colwise(function(v) mean(is.na(v) | v > 1), tests))
  betterErr <<- ddply(better, .(limited, policy), colwise(se, tests))
  better <<- ddply(better, .(limited, policy), colwise(mean, tests))
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
  xlabels = sapply(1:9, function(i) paste("O", i, sep=""))
  title = ifelse(!limited, "Objectives Completed (All Hints)", "Objectives Completed (Random Hint)")
  ggplot(data, aes(variable, value, fill=policy)) +
    geom_bar(stat='identity', position='dodge') + 
    labs(title=title, x="Objective", y="Percent Completed as Fast", fill="Policy") +
    scale_y_continuous(labels=percent, limits=c(0,1)) +
    scale_x_discrete(labels=xlabels) +
    theme_bw() + 
    scale_fill_grey()
}