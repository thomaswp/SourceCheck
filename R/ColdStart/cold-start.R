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
    xlab("Training Dataset Size") + ylab("Mean QualityScore") + labs(color="Data") +
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
  
  textSize <- 14
  ggplot(rounds, aes(x=count, y=mean, color=source, linetype=weight)) + 
    geom_line(size=0.75) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean QualityScore") + labs(color="Data", linetype="Weights") +
    scale_color_manual(labels=c("AllExpert", "OneExpert", "Students"), values=c(twoColors, "black")) +
    scale_linetype_manual(labels=c("Uniform", "Voting"), values=c("dotted", "solid")) +
    theme_bw(base_size = textSize) + theme(plot.title = element_text(hjust = 0.5, size=textSize))
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
    xlab("Training Dataset Size") + ylab("Mean QualityScore") + labs(linetype="Match") +
    scale_linetype_discrete(labels=c("Full", "Partial")) +
    theme_bw()
}

plotColdStartUnweighted <- function(rounds) {
  melted <- melt(rounds[,c(1:3,11,12)], id=c("count", "total", "assignmentID"))
  
  ggplot(melted, aes(x=count, y=value, linetype=variable)) + 
    geom_line(size=1) + facet_wrap(~assignmentID, scales = "free_x", labeller=as_labeller(assignmentNames)) +
    xlab("Training Dataset Size") + ylab("Mean QualityScore") + labs(linetype="Match") +
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

read_hints <- function(dir) {
  read_csv(paste0("../../data/hint-rating/", dir, "/analysis/", coldStartFile))
}

plotKs <- function(dir, full) {
  ktest <- read_csv(paste0("../../data/hint-rating/", dir, "/analysis/k-test-sourcecheck.csv"))
  scores <- ddply(ktest, c("k", "assignmentID"), summarize, fullMean=mean(MultipleTutors_Full), partialMean=mean(MultipleTutors_Partial))
  scores$score <- if (full) scores$fullMean else scores$partialMean
  ggplot(scores, aes(x=k, y=score)) + geom_line() + facet_wrap(~ assignmentID)
}

aied2018 <- function() {
  
  isnapDir <- "isnapF16-S17"
  itapDir <- "itapS16"
  coldStartFile <- sprintf("cold-start-%03d-%d.csv", 200, 1)
  
  isnap <- read_hints(isnapDir)
  isnapRounds <- getRounds(isnap)
  
  isnapBaselines <- parseBaselines(isnapDir)
  
  plotColdStart(isnapRounds) + labs(title="iSnap - QualityScore Cold Start")
  plotColdStartCompareWeights(isnapRounds, T, isnapBaselines) + 
    labs(title="iSnap - QualityScore Cold Start (Full)")
  plotColdStartCompareWeights(isnapRounds, F, isnapBaselines) + 
    labs(title="iSnap - QualityScore Cold Start (Partial)")
  
  iSnapStatsFull <- qualityStats(isnapRounds, T, isnapBaselines)
  iSnapStatsFull$match <- "Full"
  iSnapStatsPartial <- qualityStats(isnapRounds, F, isnapBaselines)
  iSnapStatsPartial$match <- "Partial"
  iSnapStats <- rbind(iSnapStatsFull, iSnapStatsPartial)
  iSnapStats$dataset <- "iSnap"
  
  itap <- read_hints(itapDir)
  itapRounds <- getRounds(itap)
  itapRounds$assignmentID <- ordered(itapRounds$assignmentID, c("helloWorld", "firstAndLast", "isPunctuation",
                                                                "kthDigit", "oneToN"))
  
  itapBaselines <- parseBaselines(itapDir)
  itapBaselines$assignmentID <- ordered(itapBaselines$assignmentID, c("helloWorld", "firstAndLast", "isPunctuation",
                                                                      "kthDigit", "oneToN"))
  
  plotColdStartCompareWeights(itapRounds, T, itapBaselines) + 
    labs(title="ITAP - QualityScore Cold Start (Full)")
  plotColdStartCompareWeights(itapRounds, F, itapBaselines) + 
    labs(title="ITAP - QualityScore Cold Start (Partial)")
  
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
  # sig
  wilcox.test(isnapRequests$fullMean, isnapRequests$fullEven, paired=T)
  cohen.d(isnapRequests$fullMean, isnapRequests$fullEven)
  
  isnapTemplate <- read_csv(paste("../../data/hint-rating", isnapDir, "analysis/ratings-sourcecheck-template.csv", sep="/"))
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
  # This is not needed, plus probably invalid since it includes breaking ties on 0s.
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

  # Loss of peak quality
  # Uniform weighting
  1 - isnapRound("guess1Lab", F, last) / isnapRound("guess1Lab", F, max)
  1 - isnapRound("squiralHW", F, last) / isnapRound("squiralHW", F, max)
  # Voting-based weighting
  1 - isnapRound("guess1Lab", T, last) / isnapRound("guess1Lab", T, max)
  1 - isnapRound("squiralHW", T, last) / isnapRound("squiralHW", T, max)

  # Improvement from voting-based weighting at end
  isnapRound("guess1Lab", T, last) / isnapRound("guess1Lab", F, last) - 1
  isnapRound("squiralHW", T, last) / isnapRound("squiralHW", F, last) - 1
  
  # Percent of hint requests with positive and negative slopes
  bests <- getBests(isnap)
  mean(bests$sl[bests$assignmentID == "guess1Lab"] < 0)
  mean(bests$sl[bests$assignmentID == "squiralHW"] < 0)
  mean(bests$sl[bests$assignmentID == "guess1Lab"] > 0)
  mean(bests$sl[bests$assignmentID == "squiralHW"] > 0)
  
  
  # When student data outperforms the single baseline
  surpasses("guess1Lab", "single")
  surpasses("squiralHW", "single")
  
  # Improvement of peak voting student over single baseline
  isnapRound("guess1Lab", T, max) / baseline("guess1Lab", "single") - 1
  isnapRound("squiralHW", T, max) / baseline("squiralHW", "single") - 1
  
  # How short peak voting student is of template baseline
  isnapRound("guess1Lab", T, max) / baseline("guess1Lab", "template")
  isnapRound("squiralHW", T, max) / baseline("squiralHW", "template")
}

surpasses <- function(assignment, baseline) {
  min(which(isnapRounds$fullMean[isnapRounds$assignmentID == assignment] > baseline(assignment, baseline)))
}

baseline <- function(assignment, baseline) {
  isnapBaselines$fullMean[isnapBaselines$assignmentID==assignment & isnapBaselines$type==baseline]  
}

isnapRound <- function(assignment, voting, fn) {
  col <- if (voting) isnapRounds$fullMean else isnapRounds$fullEven
  fn(col[isnapRounds$assignmentID == assignment])
}
