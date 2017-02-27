library(readr)
library(ggplot2)

loadData <- function() {
  guess1 <- read_csv("../../data/csc200/fall2016/analysis/guess1Lab/agreement.csv")
  squiral <- read_csv("../../data/csc200/fall2016/analysis/squiralHW/agreement.csv")
}

plotAll <- function(data) {
  ggplot(data[data$actual == "all" & data$edit != "keep" & data$pred != "highlight-sed",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity") + 
    facet_grid(. ~ stat)
}

plotIdeal <- function(data) {
  ggplot(data[data$actual == "all" & data$edit != "keep" & data$pred != "highlight-sed" & data$stat=="recall",], aes(x=edit, y=value)) + 
    geom_bar(aes(fill=pred), position="dodge", stat="identity")
}