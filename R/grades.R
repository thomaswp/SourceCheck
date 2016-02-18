library("plyr")
library("ggplot2")
#library("ggthemes")
library("reshape2")
library("scales")

iqMean <- function(x) {
  n <- length(x)
  start <- floor(n / 4) + 1
  end <- floor(3 * n / 4)
  subset <- sort(x)[start:end]
  return (2 * sum(subset) / n)
}

loadData <- function() {
  rm(list=ls())
  
  maxTest <<- 8;
  
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  percs <<- sapply(0:maxTest, function(i) paste("perc", i, sep=""))
  perAct <<- sapply(0:maxTest, function(i) paste("perAct", i, sep=""))
    
  grade <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/grade-p.csv")
  grade$policy <<- factor(grade$policy, levels = c("Hint All", "Hint Exemplar", "Direct Ideal", "Direct Student", "Student Next"))
  
  solve <- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/solve1-p.csv")
  grade$actions <<- solve$actions
  
  #chain <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/chain.csv")
  #chain$policy <<- factor(chain$policy, levels = c("Hint All", "Hint Exemplar", "Direct Ideal", "Direct Student", "Student Next"))
  #chain <<- rbind(grade[grade$policy=="Hint All" | grade$policy=="Hint Exemplar",], chain)
  
  for (i in 1:(maxTest+1)) {
    grade[,percs[i]] <<- grade[,tests[i]] / grade$total
  }
  
  for (i in 1:(maxTest+1)) {
    grade[,perAct[i]] <<- grade[,tests[i]] / grade$actions
  }
  
  #for (i in 1:(maxTest+1)) {
  #  chain[,percs[i]] <<- chain[,tests[i]] / chain$total
  #}
  
  students <<- sapply(unique(grade$student), as.character)
}

policies <- function(grades) {
  sapply(unique(grades$policy), as.character)
}

plotUndo <- function(grades) {
  data <- grades[grades$action=="undo" & grades$policy != "Student Next", c("policy", percs)]
  data <- melt(data, id=c("policy"))
  data <- ddply(data, .(policy, variable), summarize, mean=mean(sign(value)))
  xlabels = sapply(1:9, function(i) paste("O", i, sep=""))
  title = "Students Getting Hints that Undo Objectives"
  ggplot(data, aes(variable, mean, fill=policy)) +
    geom_bar(stat='identity', position='dodge') + 
    labs(title=title, x="Objective", y="Percent of Students", fill="Policy") +
    scale_x_discrete(labels=xlabels) +
    scale_y_continuous(labels=percent) +
    theme_bw() + 
    scale_fill_grey(labels=c("NA", "NE", "DI", "DS", "SN"))
}

plotStacked <- function(grades, action) {
  data <- grades[grades$action==action & grades$policy != "Student Next", c("policy", perAct)]
  data <- melt(data, id=c("policy"))
  data <- ddply(data, .(policy, variable), summarize, mean=mean(sign(value)))
  xlabels = sapply(1:9, function(i) paste("O", i, sep=""))
  title = "Median Hints Undoing Objectives"
  ggplot(data, aes(policy, mean, fill=variable)) +
    geom_bar(stat='identity') + 
    labs(title=title, x="Policy", y="Percent of Hints", fill="Objective") +
    # scale_x_discrete(labels=c("NA", "NE", "DI", "DS", "SN")) +
    # scale_y_continuous(labels=percent) +
    theme_bw() + 
    scale_fill_grey(labels=xlabels)
    #scale_fill_brewer(palette="RdYlGn", labels=xlabels)
    #scale_fill_manual(labels=xlabels, values=brewer_pal(palette="OrRd", direction=-1)(11)[2:10])
}

compareBoth <- function(grades, useSign) {
  grades <- grades[,c("policy", "action", percs)]
  grades <- melt(grades, id=c("policy", "action"))
  if (useSign) {
    combined <- ddply(grades, .(policy, action, variable), summarize, mean=mean(sign(value)))
  } else {
    combined <- ddply(grades, .(policy, action, variable), summarize, mean=median(value))
  }
  ggplot(combined, aes(policy, mean, fill=variable)) +
    #geom_bar(aes(fill = action), position = "dodge", stat="identity") +
    geom_bar(stat='identity') +
    facet_grid(~ action) + scale_fill_grey()
}

compare <- function(grades, action) {
  sapply(policies(grades), function(policy) {
    colSums(grades[grades$policy==policy & grades$action==action, percs]) / length(students)
  })
}

compareSign <- function(grades, action) {
  sapply(policies(grades), function(policy) {
    colSums(sign(grades[grades$policy==policy & grades$action==action, percs])) / length(students)
  })
}

compareHelpful <- function(grades) {
  sapply(policies(grades), function(policy) {
    do <- grades[grades$policy==policy & grades$action=="do", percs]
    undo <- grades[grades$policy==policy & grades$action=="undo", percs]
    return (colSums(do > undo) / length(students))
  })
}
