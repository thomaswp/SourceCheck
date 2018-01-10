library(readr)
library(ggplot2)
library(plyr)
library(reshape2)

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))

ratings <- read_csv("~/GitHub/SnapHints/data/hint-rating/isnap2017/analysis/cold-start.csv")

assignments <- ddply(ratings, c("round", "count", "total", "assignmentID"), colwise(mean))

ratings$requestID <- ordered(ratings$requestID)

requests <- ddply(ratings, c("count", "total", "assignmentID", "requestID"), summarize, 
                  fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                  partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial))

rounds <- ddply(assignments, c("count", "total", "assignmentID"), summarize, 
                  fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                  partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial))


ggplot(melt(rounds[,c(1:4, 6)], id=c("count", "total", "assignmentID")), aes(x=count, y=value, color=variable)) + 
  geom_line() + facet_grid(. ~ assignmentID)

ggplot(requests, aes(x=count, y=fullMean, color=requestID)) + geom_line() + facet_grid(. ~ assignmentID)
