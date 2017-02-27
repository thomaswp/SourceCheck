library(readr)
library(ggplot2)

loadData <- function() {
  guess1 <- read_csv("../../data/csc200/fall2016/analysis/guess1Lab/agreement.csv")
  squiral <- read_csv("../../data/csc200/fall2016/analysis/squiralHW/agreement.csv")
}

plotAll <- function(data) {
  ggplot(data[data$actual == "all" & data$edit != "keep" & data$pred != "highlight-sed",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity") + 
    geom_text(aes(group=pred, label=correct, vjust=ifelse(value>0.1,1.5,-1)), position = position_dodge(width=1)) +
    geom_text(aes(y = 0, group=pred, label=total, vjust=-1), position = position_dodge(width=1)) +
    facet_grid(. ~ stat)
}

plotIdeal <- function(data) {
  ggplot(data[data$actual == "all" & data$edit != "keep" & data$pred != "highlight-sed" & data$stat=="recall",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity")
}