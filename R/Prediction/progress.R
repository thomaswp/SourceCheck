
source("../Hints Comparison/util.R")

library(ggplot2)
library(plyr)

plotProgres <- function() {
  progress <- read.csv("../../data/csc200/fall2016/analysis/guess2HW/progress.csv")
  progress$label <- progress$grade >= 0.8
  progress$label <- ordered(ifelse(progress$grade >= 0.8, ifelse(progress$grade==1, "perfect", "good"), "bad"), c("bad", "good", "perfect"))
  progress$minute <- floor(progress$time / 60)
  
  ggplot(progress, aes(x=time, y=pProgress, color=label, group=attempt)) + geom_line()
  
  combined <- ddply(progress, c("label", "minute"), summarize, mP=mean(progress), seP=se(progress), mPP=mean(pProgress), sePP=se(pProgress), n=length(progress))
  combined <- combined[combined$n>1,]
  combined
  ggplot(combined, aes(x=minute, y=mPP, color=label)) + geom_ribbon(aes(ymin=mPP-sePP, ymax=mPP+sePP), alpha=0.2) + geom_line(aes(size=n))
  ggplot(combined, aes(x=minute, y=mP, color=label)) + geom_ribbon(aes(ymin=mP-seP, ymax=mP+seP), alpha=0.2) + geom_line(aes(size=n))
}


library(rpart)
tree <- function() {
  tree <- rpart(gradePC ~ minute + progress + pProgress, progress)
}