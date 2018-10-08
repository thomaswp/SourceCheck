library(readr)
library(ggplot2)

ratings <- read_csv("../../data/csc200/fall2017/export/guess1Lab/fall2016-rating.csv")

table(ratings$type)
inserts <- ratings[ratings$p_action=="insert",]

ggplot(inserts, aes(x=assignmentID, y=p_consensus, fill=type)) + geom_boxplot()

table(ratings$p_ordering == 1, ratings$type)
