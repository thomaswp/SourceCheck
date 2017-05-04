library(readr)
library(ggplot2)
library(plyr)

twoColors <- c("#a1d99b","#2c7fb8")

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
  return (data)
}

plotAll <- function(data) {
  ggplot(data[data$actual == "all" & data$edit != "keep" & data$pred != "highlight-sed",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity") + 
    geom_text(aes(group=pred, label=correct, vjust=ifelse(value>0.2,1.5,-1.5)), position = position_dodge(width=1), size=4.5) +
    geom_text(aes(y = 0, group=pred, label=total, vjust=-1), position = position_dodge(width=1), size=4.5) +
    facet_grid(stat ~ assignment) + 
    scale_fill_manual(name="Predictor", labels=c("SC", "TC"), values=twoColors) +
    scale_x_discrete(name="Edit Type", labels=c("Delete", "Move", "Insertion")) +
    scale_y_continuous(name="Value") +
    theme_bw(base_size = 16) +
    theme(axis.title=element_text(size=14), legend.title=element_text(size=14))
}

plotIdeal <- function(data) {
  ggplot(data[data$actual == "ideal" & data$edit != "keep" & data$pred != "highlight-sed" & data$stat=="Recall",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity") + 
    geom_text(aes(group=pred, label=correct, vjust=ifelse(value>0.2,1.5,-1.5)), position = position_dodge(width=1), size=4.5) +
    geom_text(aes(y = 0, group=pred, label=total, vjust=-1), position = position_dodge(width=1), size=4.5) +
    facet_grid(. ~ assignment) + 
    scale_fill_manual(name="Predictor", labels=c("SC", "TC"), values=twoColors) +
    scale_x_discrete(name="Edit Type", labels=c("Delete", "Move", "Insertion")) +
    scale_y_continuous(name="Recall") +
    theme_bw(base_size = 16) +
    theme(axis.title=element_text(size=14), legend.title=element_text(size=14))
}

hintCounts <- function() {
  sqHighlight <- c(11, 7, 3, 3, 5, 4, 2, 6, 4, 2, 15, 17, 4, 3, 5, 8, 3, 8, 11, 7, 5, 5)
  summary(sqHighlight); sd(sqHighlight);
  sqHuman <- c(13, 8, 4, 3, 4, 5, 2, 7, 6, 5, 3, 2, 4, 4, 8, 9, 7, 1, 3, 7, 2, 3, 
               10, 8, 4, 5, 6, 5, 3, 7, 6, 6, 2, 3, 6, 6, 7, 9, 6, 1, 6, 8, 2, 3)
  summary(sqHuman); sd(sqHuman)
  ggHighlight <- c(8, 6, 21, 14, 12, 12, 4, 5, 18, 17, 12, 10, 7, 11, 10, 10, 10, 11, 10, 9, 10, 11, 10, 13, 8, 8, 13, 6, 10)
  summary(ggHighlight); sd(ggHighlight)
  ggHuman <- c(4, 2, 17, 10, 8, 9, 3, 4, 1, 2, 8, 7, 9, 14, 4, 5, 1, 1, 8, 3, 5, 7, 10, 0, 13, 7, 10, 4, 1,
               4, 2, 20, 10, 10, 11, 4, 4, 3, 1, 7, 5, 8, 12, 3, 5, 1, 1, 6, 3, 4, 5, 10, 0, 12, 10, 14, 8, 3)
  summary(ggHuman); sd(ggHuman)
  
  counts <- rbind(
    data.frame(values=sqHighlight, type="sqHighlight"),
    data.frame(values=sqHuman, type="sqHuman"),
    data.frame(values=ggHighlight, type="ggHighlight"),
    data.frame(values=ggHuman, type="ggHuman")
  )
  ggplot(counts, aes(x=type, y=values)) + geom_violin() + geom_boxplot(width=0.1)
}

squash <- function() {
  totals <- ddply(data[data$edit != "keep" & data$pred != "highlight-sed",], c("assignment", "actual",  "stat", "pred"), summarize, tCorrect=sum(correct), tTotal=sum(total), tValue=tCorrect/tTotal)
  totalsKeep <- ddply(data[data$edit == "keep" & data$pred != "highlight-sed",], c("assignment", "actual",  "stat", "pred"), summarize, tCorrect=sum(correct), tTotal=sum(total), tValue=tCorrect/tTotal)
}