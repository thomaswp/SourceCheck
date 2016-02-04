library("plyr")
library("ggplot2")

se <- function(x) sqrt(var(x)/length(x))

prediction <- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/prediction.csv")
prediction$percActions = prediction$predicted / prediction$actions
prediction$percHints = prediction$predicted / prediction$hints

combined <<- ddply(prediction, .(policy), summarize, 
                   actionsMean = mean(percActions), actionsSE = se(percActions), 
                   hintsMean = mean(percHints), hintsSE = se(percHints),
                   corActions = cor(percActions, grade), corHints = cor(percHints, grade))