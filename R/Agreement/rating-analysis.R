library(readr)
library(ggplot2)

ratings <- read_csv("../../data/csc200/fall2017/export/guess1Lab/fall2016-rating.csv")

table(ratings$type)
inserts <- ratings[ratings$p_action=="insert",]

ggplot(inserts, aes(y=p_ordering, x=type)) + geom_boxplot()
