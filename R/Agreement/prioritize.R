library(plyr)
library(ggplot2)
library(reshape2)

prioritize <- read_csv("../../data/csc200/spring2017/analysis/edm2017-prioritize.csv")
prioritize$category <- ordered(prioritize$category, c("bad", "ok", "ideal"))

ggplot(prioritize, aes(x=assignment, y=consensus, fill=category)) + geom_boxplot()
ggplot(prioritize, aes(x=assignment, y=creation, fill=category)) + geom_boxplot()
ggplot(prioritize, aes(x=consensus, y=creation, color=category, shape=assignment)) + geom_point()
