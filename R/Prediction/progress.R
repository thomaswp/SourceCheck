
source("../Hints Comparison/util.R")

library(ggplot2)
library(plyr)

plotProgres <- function() {
  progress <- read.csv("../../data/csc200/fall2016/analysis/squiralHW/progress.csv")
  progress$label <- progress$grade >= 0.8
  # progress$label <- ordered(ifelse(progress$grade >= 0.8, ifelse(progress$grade==1, "perfect", "good"), "bad"), c("bad", "good", "perfect"))
  progress$minute <- floor(progress$time / 60)
  
  ggplot(progress, aes(x=time, y=progress, color=label, group=attempt)) + geom_line()
  ggplot(progress, aes(x=time, y=pProgress, color=label, group=attempt)) + geom_line()
  
  combined <- ddply(progress, c("label", "minute"), summarize, mP=mean(progress), seP=se(progress), mPP=mean(pProgress), sePP=se(pProgress), n=length(progress))
  combined <- combined[combined$n>1,]
  ggplot(combined, aes(x=minute, y=mPP, color=label)) + geom_ribbon(aes(ymin=mPP-sePP, ymax=mPP+sePP), alpha=0.2) + geom_line(aes(size=n))
  ggplot(combined, aes(x=minute, y=mP, color=label)) + geom_ribbon(aes(ymin=mP-seP, ymax=mP+seP), alpha=0.2) + geom_line(aes(size=n))
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
  ddply(progress, c("attempt"), summarize, minIndex=tail(which(diff == min(diff)), 1))  
}

library(rpart)
tree <- function() {
  tree <- rpart(gradePC ~ minute + progress + pProgress, progress)
}