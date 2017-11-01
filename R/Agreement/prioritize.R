library(plyr)
library(ggplot2)
library(reshape2)
library(readr)

simple_roc <- function(labels, scores){
  labels <- labels[order(scores, decreasing=TRUE)]
  scores <- scores[order(scores, decreasing=TRUE)]
  data <- data.frame(TPR=cumsum(labels)/sum(labels), FPR=cumsum(!labels)/sum(!labels), cutoff=scores)
  ggplot(data, aes(y = TPR, x=FPR, color=scores)) + geom_point()
}

prioritize <- read_csv("../../data/csc200/spring2017/analysis/edm2017-prioritize.csv")
prioritize$category <- ordered(prioritize$category, c("bad", "ok", "ideal"))
prioritize$good <- prioritize$category != "bad"

ggplot(prioritize, aes(x=assignment, y=consensus, fill=category)) + geom_boxplot()
ggplot(prioritize, aes(x=assignment, y=consensusNum, fill=category)) + geom_boxplot()
ggplot(prioritize, aes(x=assignment, y=creation, fill=category)) + geom_boxplot()
ggplot(prioritize, aes(x=assignment, y=ordering, fill=category)) + geom_boxplot()
ggplot(prioritize, aes(x=consensus, y=creation, color=category, shape=assignment)) + geom_point()

table(prioritize$category, prioritize$type)
table(prioritize$category, prioritize$consensus > .35)
simple_roc(prioritize$good, prioritize$consensus)

table(prioritize$category, prioritize$ordering > .35)
inserts <- prioritize[prioritize$type=="insert",]
inserts <- inserts[!is.na(inserts$ordering),]
simple_roc(inserts$good, inserts$ordering)
