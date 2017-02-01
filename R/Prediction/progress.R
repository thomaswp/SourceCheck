
source("../Hints Comparison/util.R")

library(ggplot2)
library(plyr)

plotProgres <- function() {
  progress <- read.csv("../../data/csc200/fall2016/analysis/squiralHW/progress.csv")
  progress$pass <- progress$grade >= 0.8
  
  ggplot(progress, aes(x=time, y=pProgress, color=pass, group=attempt)) + geom_line()
  
  progress$minute <- floor(progress$time / 60)
  combined <- ddply(progress, c("pass", "minute"), summarize, mP=mean(progress), seP=se(progress), mPP=mean(pProgress), sePP=se(pProgress), n=length(progress))
  ggplot(combined, aes(x=minute, y=mPP, color=pass)) + geom_ribbon(aes(ymin=mPP-sePP, ymax=mPP+sePP), alpha=0.2) + geom_line(aes(size=n))
}
