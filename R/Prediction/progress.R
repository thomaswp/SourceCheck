
source("../Hints Comparison/util.R")

library(ggplot2)
library(plyr)

plotProgres <- function() {
  progress <- read.csv("../../data/csc200/fall2016/analysis/squiralHW/progress.csv")
  progress$label <- progress$grade >= 0.8
  # progress$label <- ordered(ifelse(progress$grade >= 0.8, ifelse(progress$grade==1, "perfect", "good"), "bad"), c("bad", "good", "perfect"))
  progress$minute <- floor(progress$time / 60)
  progress$size <- progress$progress / progress$pProgress
  progress$excess <- progress$progress - progress$size
  
  ggplot(progress, aes(x=time, y=progress, color=label, group=attempt)) + geom_line()
  ggplot(progress, aes(x=time, y=pProgress, color=label, group=attempt)) + geom_line()
  
  combined <- ddply(progress, c("label", "minute"), summarize, mP=mean(progress), medP=median(progress), seP=se(progress), mPP=mean(pProgress), medPP=median(pProgress), sePP=se(pProgress), mSize=mean(size), seSize=se(size), n=length(progress))
  combined <- combined[combined$n>1,]
  ggplot(combined, aes(x=minute, y=mPP, color=label)) + geom_ribbon(aes(ymin=mPP-sePP, ymax=mPP+sePP), alpha=0.2) + geom_line(aes(size=n))
  ggplot(combined, aes(x=minute, y=medPP, color=label)) + geom_line(aes(size=n))
  ggplot(combined, aes(x=minute, y=mP, color=label)) + geom_ribbon(aes(ymin=mP-seP, ymax=mP+seP), alpha=0.2) + geom_line(aes(size=n))
  ggplot(combined, aes(x=minute, y=medP, color=label)) + geom_line(aes(size=n))
  ggplot(combined, aes(x=minute, y=mSize, color=label)) + geom_ribbon(aes(ymin=mSize-seSize, ymax=mSize+seSize), alpha=0.2) + geom_line(aes(size=n))
  
  agg <- ddply(progress[progress$minute > 2 & progress$minute <= 10,], c("attempt", "label"), summarize, 
               minP=min(progress), maxP=max(progress), meanP=mean(progress), 
               minEx=min(excess), maxEx=max(excess), meanEx=mean(excess), 
               minPP=min(pProgress), maxPP=max(pProgress), meanPP=mean(pProgress), 
               end=max(minute))
  agg <- agg[agg$end == max(agg$end),]
  ggplot(agg, aes(x=label, y=meanP)) + geom_violin() + geom_boxplot(width=0.2)
  ggplot(agg, aes(x=label, y=meanPP)) + geom_violin() + geom_boxplot(width=0.2)
  ggplot(agg, aes(x=label, y=maxEx)) + geom_violin() + geom_boxplot(width=0.2)
}


buildStudents <- function() {
  students <- NA
  for (attempt in unique(progress$attempt)) {
    row <- data.frame(attempt=c(attempt))
    points <- progress[progress$attempt == attempt,]
    row$label <- points$label[1]
    for (i in 1:10) {
      prog <- points$progress[points$minute==i]
      if (length(prog) == 0) prog <- NA
      row[,paste0("prog", i)] <- prog
      
      pProg <- points$pProgress[points$minute==i]
      if (length(pProg) == 0) pProg <- NA
      row[,paste0("pProg", i)] <- prog
    }
    students <- rbind(students, row)
  }
  students[-1,]
}

snapshots <- function() {
  m <- 10
  progress$diff <- abs(m - progress$minute)
  snapshots <- ddply(progress, c("attempt", "label"), summarize, minIndex=tail(which(diff == min(diff)), 1), minute=minute[minIndex], rowID=rowID[minIndex])  
}

library(rpart)
tree <- function() {
  tree <- rpart(gradePC ~ minute + progress + pProgress, progress)
}