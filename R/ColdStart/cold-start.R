library(readr)
library(ggplot2)
library(plyr)
library(reshape2)

twoColors <- c("#a1d99b","#2c7fb8")

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))

plotColdStart <- function(ratings) {
  assignments <- ddply(ratings, c("round", "count", "total", "assignmentID"), colwise(mean))
  
  rounds <- ddply(assignments, c("count", "total", "assignmentID"), summarize, 
                  fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                  partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial))
  
  assignmentNames <- c(
    `guess1Lab`="Guessing Game",
    `squiralHW`="Squiral"
  )
  
  ggplot(melt(rounds[,c(1:4, 6)], id=c("count", "total", "assignmentID")), aes(x=count, y=value, color=variable)) + 
    geom_line() + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean Quality Score") + labs(color="Match") +
    scale_color_manual(labels=c("Full", "Partial"), values=twoColors) +
    theme_bw()
}

plotRequests <- function(ratings) {
  ratings$requestID <- ordered(ratings$requestID)
  
  requests <- ddply(ratings, c("count", "total", "assignmentID", "requestID"), summarize, 
                    fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                    partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial))
  
  
  ggplot(requests, aes(x=count, y=fullMean, color=requestID)) + geom_line() + facet_grid(. ~ assignmentID)
}

plotRequestBoxplots <- function(ratings) {
  ggplot(ratings[ratings$assignmentID=="guess1Lab",], 
         aes(x=ordered(count), y=MultipleTutors_Partial)) + geom_boxplot() + facet_grid(requestID ~ .)
}

runme <- function() {
  isnap <- read_csv("../../data/hint-rating/isnap2017/analysis/cold-start.csv")
  
  plotColdStart(isnap) + labs(title="iSnap - Quality Cold Start") + scale_y_continuous(limits=c(0,.3))
}
