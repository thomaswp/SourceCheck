library(readr)
library(ggplot2)
library(plyr)
library(reshape2)

traces <- read_csv("../../data/hint-rating/isnap2017/analysis/traces.csv")
dists <- read_csv("../../data/hint-rating/isnap2017/analysis/distances.csv")

pairs <- merge(traces, dists, by=c("traceID", "requestID"))
pairs$quality <- pairs$MultipleTutors_Full

minCosts <- sapply(pairs$requestID, function(id) min(pairs[pairs$requestID==id,]$cost))
maxCosts <- sapply(pairs$requestID, function(id) max(pairs[pairs$requestID==id,]$cost))
percentiles <- sapply(pairs$requestID, function(id) ecdf(pairs[pairs$requestID==id,]$cost))
pairs$normCost <- (pairs$cost - minCosts) / (maxCosts - minCosts)
pairs$pCost <- sapply(1:nrow(pairs), function(i) ecdf(pairs[pairs$requestID==pairs$requestID[[i]],]$cost)(pairs$cost[[i]]))

pairs$costQuartile <- as.ordered(as.integer(pairs$pCost * 3.999))

best <- ddply(pairs, "requestID", summarize, maxQuality = max(quality), maxCount = sum(quality==1))
hist(best$maxQuality)

ggplot(pairs, aes(x=pCost, y=quality)) + geom_jitter(height=0.05)
ggplot(pairs, aes(x=pCost, y=quality)) + geom_jitter(height=0.05) + facet_wrap(~requestID)
ggplot(pairs, aes(x=costQuartile, y=quality)) + geom_boxplot() + facet_wrap(~requestID)
