library("plyr")
library("ggplot2")
library("scales")

se <- function(x) sqrt(var(x)/length(x))

loadData <- function() {
  rm(list=ls())
  maxTest <<- 8;
  complete <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/complete.csv")
  complete$policy <<- factor(complete$policy, levels = c("Hint All", "Hint Exemplar", "Direct Ideal", "Direct Student"))
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  complete$grade <<- rowSums(complete[,tests]) / 9
  combined <<- ddply(complete, .(policy, slice), summarize, 
                     gradeMean = mean(grade), gradeSE = se(grade), perfect=mean(grade==1),
                     stepsMean = mean(steps), stepsSE = se(steps), 
                     deletionsMean = mean(deletions), deletionsSE = se(deletions),
                     studentStepsMean = mean(studentSteps), studentStepsSE = se(studentSteps), 
                     hashCount = length(unique(hash)))
  twoColors <<- c("#a1d99b","#2c7fb8")
  
  completeAll <<- complete
  combinedAll <<- combined
  
  complete <<- complete[complete$policy == "Hint All" | complete$policy == "Hint Exemplar",]
  combined <<- combined[combined$policy == "Hint All" | combined$policy == "Hint Exemplar",]
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
  ggplot(combined, aes(x=slice+1, y=hashCount, colour=policy)) + 
    labs(title="Unique Solutions", x="Slice", y="Unique Solutions", colour="Policy") +
    geom_line() +
    geom_point() +
    scale_color_manual(values=twoColors, labels=c("NA", "NE")) +
    theme_bw()
}


plotGrade <- function() {
  ggplot(combined, aes(x=slice+1, y=gradeMean, color=policy)) + 
    labs(title="Final Solution Grade", x="Slice", y="Grade", color="Policy") +
    geom_line() +
    geom_ribbon(aes(x=slice+1, ymin=gradeMean-gradeSE, ymax=gradeMean+gradeSE, fill=policy), color=NA, alpha=.3) +
    guides(fill=FALSE) +
    geom_point() +
    theme_bw() + 
    scale_fill_manual(values=twoColors) +
    scale_color_manual(values=twoColors, labels=c("NA", "NE")) +
    scale_y_continuous(limits=c(0.85, 1), label=percent)
}

plotDeletions <- function() {
  ggplot(combinedAll, aes(x=slice+1, y=deletionsMean, color=policy)) + 
    labs(title="Final Solution Grade", x="Slice", y="Grade", color="Policy") +
    geom_ribbon(aes(x=slice+1, ymin=deletionsMean-deletionsSE, ymax=deletionsMean+deletionsSE, fill=policy), color=NA, alpha=.3) +
    geom_line() +
    guides(fill=FALSE) +
    geom_point() +
    theme_bw() + 
    #scale_x_continuous(limits = c(40, 50)) + 
    #scale_fill_brewer() +
    #scale_color_brewer() + #, labels=c("NA", "NE", "DI", "DS")) +
    scale_y_continuous()
}

plotPerfect <- function() {
  ggplot(combined, aes(x=slice+1, y=perfect, color=policy)) + 
    labs(title="Final Solution Grade", x="Slice", y="Grade", fill="Policy") +
    geom_line() +
    geom_point() +
    theme_bw() + 
    scale_y_continuous(label=percent)
}

plotSteps <- function() {
  ggplot(combined, aes(x=slice+1, y=stepsMean, colour=policy)) + 
    labs(title="Hints to Final Solution", x="Slice", y="Hints", color="Policy") +
    geom_line() +
    geom_ribbon(aes(x=slice+1, ymin=stepsMean-stepsSE, ymax=stepsMean+stepsSE, fill=policy), color=NA, alpha=.3) +
    guides(fill=FALSE) +
    geom_point() +
    scale_fill_manual(values=twoColors) +
    scale_color_manual(values=twoColors, labels=c("NA", "NE")) +
    theme_bw()
}

plotStudentSteps <- function() {
  ggplot(combined[combined$policy=="Hint All",], aes(x=slice, y=studentStepsMean)) + 
    geom_errorbar(aes(ymin=studentStepsMean-studentStepsSE, ymax=studentStepsMean+studentStepsSE), width=.4) +
    geom_line() +
    geom_point()  
}

