library(readr)
library(ggplot2)
library(plyr)
library(reshape2)
library(car)

source("../Hints Comparison/util.R")

twoColors <- c("#a1d99b","#2c7fb8")

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))
first <- function(x) head(x, 1)
last <- function(x) tail(x, 1)

slope <- function(x) {
  df <- data.frame(idx=1:length(x), x=x)
  model <- lm(x ~ idx, df)
  return (model$coefficients["idx"][[1]])
}

lateSlope <- function(x) {
  x <- x[length(x)/2+1:length(x)]
  df <- data.frame(idx=1:length(x), x=x)
  model <- lm(x ~ idx, df)
  return (model$coefficients["idx"][[1]])
}

createTemplateCopy <- function(template, source, means) {
  means <- means[means$type == source,]
  assignments <- unique(template$assignmentID)
  for (assignment in assignments) {
    template[template$assignmentID == assignment,]$mean <- means$mean[means$assignmentID==assignment]
  }
  template$source <- source
  return (template)
}


assignmentNames <- c(
  `guess1Lab` = "GuessingGame",
  `squiralHW` = "Squiral",
  `helloWorld` = "HelloWorld",
  `firstAndLast` = "FirstAndLast",
  `oneToN` = "OneToN",
  `isPunctuation` = "IsPunctuation",
  `kthDigit` = "KthDigit"
)

getRounds <- function(ratings) {
  # These need to be calculated before taking the mean; otherwise, fractions will be averaged
  # by averaging their numerators and denominators
  ratings$fullEven <- ratings$MultipleTutors_Full_validCount / ratings$totalCount
  ratings$partialEven <- ratings$MultipleTutors_Partial_validCount / ratings$totalCount

  assignments <- ddply(ratings[,-5], c("round", "count", "total", "assignmentID"), colwise(mean))
  
  rounds <- ddply(assignments, c("count", "total", "assignmentID"), summarize, 
                  fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                  partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial),
                  fullWeight=mean(MultipleTutors_Full_validWeight),
                  partialWeight=mean(MultipleTutors_Partial_validWeight),
                  totalWeight=mean(totalWeight),
                  fullEven=mean(fullEven),
                  partialEven=mean(partialEven))
}

plotColdStartBounded <- function(rounds, isFull, baselines) {
  rounds$source <- "students"
  rounds$mean <- if(isFull) rounds$fullMean else rounds$partialMean
  
  baselines$mean = if (isFull) baselines$fullMean else baselines$partialMean
  bounds <- rounds
  if (!missing(baselines)) {
    bounds <- rbind(bounds, createTemplateCopy(rounds, "template", baselines))
    bounds <- rbind(bounds, createTemplateCopy(rounds, "single", baselines))
  }
  rounds <- bounds
  
  rounds$source <- ordered(rounds$source, c("template", "single", "students"))
  
  ggplot(rounds, aes(x=count, y=mean, color=source)) + 
    geom_line(size=1) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean Quality Score") + labs(color="Data") +
    scale_color_manual(labels=c("Expert All", "Expert 1", "Students"), values=c(twoColors, "black")) +
    theme_bw()
}

plotColdStartCompareWeights <- function(rounds, isFull, baselines) {
  rounds$source <- "students"
  rounds$mean <- if(isFull) rounds$fullMean else rounds$partialMean
  rounds$weight <- "weighted"
  
  roundsEven <- rounds
  roundsEven$mean <- if(isFull) roundsEven$fullEven else roundsEven$partialEven
  roundsEven$weight <- "even"
  
  baselines$mean = if (isFull) baselines$fullMean else baselines$partialMean
  
  bounds <- rounds
  if (!missing(baselines)) {
    bounds <- rbind(bounds, createTemplateCopy(rounds, "template", baselines))
    bounds <- rbind(bounds, createTemplateCopy(rounds, "single", baselines))
  }
  rounds <- bounds
  
  rounds <- rbind(rounds, roundsEven)
  
  rounds$source <- ordered(rounds$source, c("template", "single", "students"))
  
  ggplot(rounds, aes(x=count, y=mean, color=source, linetype=weight)) + 
    geom_line(size=1) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean Quality Score") + labs(color="Data", linetype="Weights") +
    scale_color_manual(labels=c("AllExpert", "OneExpert", "Students"), values=c(twoColors, "black")) +
    scale_linetype_manual(labels=c("Uniform", "Voting"), values=c("dashed", "solid")) +
    theme_bw()
}

plotColdStartWeights <- function(rounds, isFull) {
  rounds$weight <- if(isFull) rounds$fullWeight else rounds$partialWeight
  rounds$mean <- if(isFull) rounds$fullMean else rounds$partialMean
  rounds$mean2 <- rounds$weight / rounds$totalWeight
  rounds$badWeight <- rounds$totalWeight - rounds$weight
  rounds$totalWeight <- 1 / rounds$totalWeight
  
  n <- ncol(rounds)
  
  melted <- melt(rounds[,c(1:3, (n-4):n)], id=c("count", "total", "assignmentID"))
  
  ggplot(melted, aes(x=count, y=value, color=variable)) + 
    geom_line(size=1) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean Weight") + labs(color="Weight") +
    theme_bw()
}

plotColdStart <- function(rounds) {
  melted <- melt(rounds[,c(1:4, 6)], id=c("count", "total", "assignmentID"))
  
  ggplot(melted, aes(x=count, y=value, linetype=variable)) + 
    geom_line(size=1) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean Quality Score") + labs(linetype="Match") +
    scale_linetype_discrete(labels=c("Full", "Partial")) +
    theme_bw()
}

plotColdStartUnweighted <- function(rounds) {
  melted <- melt(rounds[,c(1:3,11,12)], id=c("count", "total", "assignmentID"))
  
  ggplot(melted, aes(x=count, y=value, linetype=variable)) + 
    geom_line(size=1) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean Quality Score") + labs(linetype="Match") +
    scale_linetype_discrete(labels=c("Full", "Partial")) +
    theme_bw()
}

plotRequests <- function(ratings) {
  ratings$requestID <- ordered(ratings$requestID)
  
  requests <- ddply(ratings, c("count", "total", "assignmentID", "requestID"), summarize, 
                    fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                    partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial))
  
  
  ggplot(requests, aes(x=count, y=fullMean, color=requestID)) + geom_line() + facet_wrap(~ assignmentID, scales="free_x")
}

plotRequestBoxplots <- function(ratings) {
  ggplot(ratings[ratings$assignmentID=="guess1Lab",], 
         aes(x=ordered(count), y=MultipleTutors_Partial)) + geom_boxplot() + facet_grid(requestID ~ .)
}

qualityStats <- function(rounds, isFull, baselines) {
  rounds$mean <- if (isFull) rounds$fullMean else rounds$partialMean
  stats <- ddply(rounds, "assignmentID", summarize, bestStudents=count[first(which(mean==max(mean)))],
                 maxRating=max(mean),maxStudents=max(count), finalRating=last(mean))
  
  baselines$mean = if (isFull) baselines$fullMean else baselines$partialMean
  baselines <- baselines[order(baselines$assignmentID),]
  stats$single <- baselines$mean[baselines$type == "single"]
  stats$template <- baselines$mean[baselines$type == "template"]
  stats
}

plotNegSlopeCurve <- function(ratings, isFull) {
  bests <- getBests(ratings)
  negIDs <- bests$requestID[bests$sl < 0]
  negRatings <- ratings[ratings$requestID %in% negIDs,]
  plotColdStartWeights(getRounds(negRatings), isFull)
}

getBests <- function(ratings) {
  requests <- ddply(ratings, c("count", "total", "assignmentID", "requestID"), summarize, 
                    fullMean=mean(MultipleTutors_Full), fullSE=se(MultipleTutors_Full),
                    partialMean=mean(MultipleTutors_Partial), partialSE=se(MultipleTutors_Partial),
                    fullEven=mean(MultipleTutors_Full_validCount / totalCount),
                    partialEven=mean(MultipleTutors_Partial_validCount / totalCount))
  best <- ddply(requests, c("assignmentID", "requestID", "total"), summarize, 
                best=max(fullMean), bestCount=count[best==fullMean][[1]], sl=slope(fullMean),
                bestEven=max(fullEven), bestCountEven=count[bestEven==fullEven][[1]], slEven=slope(fullEven))
  best$bestPerc <- best$bestCount / best$total
  return (best)
}

getBaseline <- function(ratings) {
  ddply(ratings, c("assignmentID"), summarize, fullMean=mean(MultipleTutors_Full), partialMean=mean(MultipleTutors_Partial))
}

parseBaselines <- function(dir) {
  template <- getBaseline(read_csv(paste("../../data/hint-rating", dir, "analysis/ratings-sourcecheck-template.csv", sep="/")))
  template$type <- "template"
  
  single <- getBaseline(read_csv(paste("../../data/hint-rating", dir, "analysis/ratings-sourcecheck-expert1.csv", sep="/")))
  single$type <- "single"
  return(rbind(template,single))
}

runme <- function() {
  isnap <- read_csv("../../data/hint-rating/isnap2017/analysis/cold-start.csv")
  isnapRounds <- getRounds(isnap)
  
  isnapBaselines <- parseBaselines("isnap2017")
  
  plotColdStart(isnapRounds) + labs(title="iSnap - Quality Cold Start")
  plotColdStartCompareWeights(isnapRounds, T, isnapBaselines) + 
    labs(title="iSnap - Quality Cold Start (Full)")
  plotColdStartCompareWeights(isnapRounds, F, isnapBaselines) + 
    labs(title="iSnap - Quality Cold Start (Partial)")
  
  iSnapStatsFull <- qualityStats(isnapRounds, T, isnapBaselines)
  iSnapStatsFull$match <- "Full"
  iSnapStatsPartial <- qualityStats(isnapRounds, F, isnapBaselines)
  iSnapStatsPartial$match <- "Partial"
  iSnapStats <- rbind(iSnapStatsFull, iSnapStatsPartial)
  iSnapStats$dataset <- "iSnap"
  
  itap <- read_csv("../../data/hint-rating/itap2016/analysis/cold-start.csv")
  itapRounds <- getRounds(itap)
  itapRounds$assignmentID <- ordered(itapRounds$assignmentID, c("helloWorld", "firstAndLast", "isPunctuation",
                                                                "kthDigit", "oneToN"))
  
  itapBaselines <- parseBaselines("itap2016")
  itapBaselines$assignmentID <- ordered(itapBaselines$assignmentID, c("helloWorld", "firstAndLast", "isPunctuation",
                                                                      "kthDigit", "oneToN"))
  
  plotColdStartCompareWeights(itapRounds, T, itapBaselines) + 
    labs(title="ITAP - Quality Cold Start (Full)")
  plotColdStartCompareWeights(itapRounds, F, itapBaselines) + 
    labs(title="ITAP - Quality Cold Start (Partial)")
  
  itapStatsFull <- qualityStats(itapRounds, T, itapBaselines)
  itapStatsFull$match <- "Full"
  itapStatsPartial <- qualityStats(itapRounds, F, itapBaselines)
  itapStatsPartial$match <- "Partial"
  itapStats <- rbind(itapStatsFull, itapStatsPartial)
  itapStats$dataset <- "ITAP"
  
  allStats <- rbind(iSnapStats, itapStats)
  write.csv(allStats, "C:/Users/Thomas/Desktop/stats.csv")
  
  ddply(getBests(isnap), c("assignmentID"), summarize, down=mean(sl < 0), up=mean(sl > 0), 
        downEven=mean(slEven < 0), upEven=mean(slEven > 0))
  ddply(getBests(itap), c("assignmentID"), summarize, down=mean(sl < 0), up=mean(sl > 0), 
        downEven=mean(slEven < 0), upEven=mean(slEven > 0))
  
  ddply(isnapRounds, "assignmentID", summarize, 
        maxVote=max(fullMean), finalVote=last(fullMean), lossVote=finalVote/maxVote,
        maxEven=max(fullEven), finalEven=last(fullEven), lossEven=finalEven/maxEven)
  ddply(itapRounds, "assignmentID", summarize, 
        maxVote=max(partialMean), finalVote=last(partialMean), lossVote=finalVote/maxVote,
        maxEven=max(partialEven), finalEven=last(partialEven), lossEven=finalEven/maxEven)
  
  isnapFinal <- isnap[isnap$count == isnap$total,]
  isnapRequests <- ddply(isnapFinal, c("assignmentID", "requestID"), summarize, 
                         fullMean=mean(MultipleTutors_Full), 
                         fullEven=mean(MultipleTutors_Full_validCount / totalCount))
  # sig
  wilcox.test(isnapRequests$fullMean[isnapRequests$assignmentID=="guess1Lab"], 
              isnapRequests$fullEven[isnapRequests$assignmentID=="guess1Lab"], paired=T)
  # not-sig
  wilcox.test(isnapRequests$fullMean[isnapRequests$assignmentID=="squiralHW"], 
              isnapRequests$fullEven[isnapRequests$assignmentID=="squiralHW"], paired=T)
  #sig
  wilcox.test(isnapRequests$fullMean, isnapRequests$fullEven, paired=T)
  cohen.d(isnapRequests$fullMean, isnapRequests$fullEven)
  
  isnapTemplate <- read_csv("../../data/hint-rating/isnap2017/analysis/ratings-sourcecheck-template.csv")
  isnapTemplate$template <- isnapTemplate$MultipleTutors_Full
  isnapRequests <- merge(isnapRequests, isnapTemplate[,c("requestID", "template")], by="requestID")
  
  # not-sig
  wilcox.test(isnapRequests$fullMean[isnapRequests$assignmentID=="guess1Lab"], 
              isnapRequests$template[isnapRequests$assignmentID=="guess1Lab"], paired=T)
  # not-sig
  wilcox.test(isnapRequests$fullMean[isnapRequests$assignmentID=="squiralHW"], 
              isnapRequests$template[isnapRequests$assignmentID=="squiralHW"], paired=T)
  # not-sig
  wilcox.test(isnapRequests$fullMean, isnapRequests$template, paired=T)
  cohen.d(isnapRequests$template, isnapRequests$fullMean)
  
  # Maybe sig without ties (also true got just GG1)?
  ps <- sapply(1:1000, function(i) wilcox.test(isnapRequests$fullMean, jitter(isnapRequests$template, amount=0.001), paired=T)$p.value)
  mean(ps < 0.05)
  mean(ps)
  
  itapFinal <- itap[itap$count == itap$total,]
  itapRequests <- ddply(itapFinal, c("assignmentID", "requestID"), summarize, 
                         partialMean=mean(MultipleTutors_Partial), 
                         partialEven=mean(MultipleTutors_Partial_validCount / totalCount))
  # not sig
  wilcox.test(itapRequests$partialMean, itapRequests$partialEven, paired=T)
  cohen.d(itapRequests$partialMean, itapRequests$partialEven)
}
