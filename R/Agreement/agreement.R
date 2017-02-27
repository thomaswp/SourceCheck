library(readr)
library(ggplot2)
library(plyr)

loadData <- function() {
  guess1 <- read_csv("../../data/csc200/fall2016/analysis/guess1Lab/agreement.csv")
  squiral <- read_csv("../../data/csc200/fall2016/analysis/squiralHW/agreement.csv")
  
  guess1$assignment <- "GG"
  squiral$assignment <- "SQ"
  data <- rbind(squiral, guess1)
  data$pred <- ordered(data$pred, c("highlight", "highlight-sed", "all"))
  data$edit <- ordered(data$edit, c("keep", "delete", "move", "insert"))
  data$stat <- paste0(toupper(substring(data$stat, 1,1)), substring(data$stat, 2))
  data$stat <- ordered(data$stat, c("Recall", "Precision"))
}

plotAll <- function(data) {
  ggplot(data[data$actual == "all" & data$edit != "keep" & data$pred != "highlight-sed",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity") + 
    geom_text(aes(group=pred, label=correct, vjust=ifelse(value>0.1,1.5,-1)), position = position_dodge(width=1)) +
    geom_text(aes(y = 0, group=pred, label=total, vjust=-1), position = position_dodge(width=1)) +
    facet_grid(stat ~ assignment) + 
    scale_fill_discrete(name="Predictor", labels=c("Algorithm", "Experts")) +
    scale_x_discrete(name="Edit Type", labels=c("Delete", "Move", "Insert")) +
    scale_y_continuous(name="Value")
}

plotIdeal <- function(data) {
  ggplot(data[data$actual == "ideal" & data$edit != "keep" & data$pred != "highlight-sed" & data$stat=="Recall",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity") + 
    geom_text(aes(group=pred, label=correct, vjust=ifelse(value>0.1,1.5,-1)), position = position_dodge(width=1)) +
    geom_text(aes(y = 0, group=pred, label=total, vjust=-1), position = position_dodge(width=1)) +
    facet_grid(. ~ assignment) + 
    scale_fill_discrete(name="Predictor", labels=c("Algorithm", "Experts")) +
    scale_x_discrete(name="Edit Type", labels=c("Delete", "Move", "Insert")) +
    scale_y_continuous(name="Recall")
}

squash <- function() {
  totals <- ddply(data[data$edit != "keep" & data$pred != "highlight-sed",], c("assignment", "actual",  "stat", "pred"), summarize, tCorrect=sum(correct), tTotal=sum(total), tValue=tCorrect/tTotal)
}