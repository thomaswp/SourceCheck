
library(plyr)
library(ggplot2)

source("../Hints Comparison/util.R")

daisy <- read.csv("data/hs/daisy-grading.csv")

daisy$GroupA <- daisy$GroupA == "1"

daisy$nComplete <- sapply(1:nrow(daisy), function(i) sum(daisy[i,3:13] == 2))
daisy$nAttempted <- sapply(1:nrow(daisy), function(i) sum(daisy[i,3:13] >= 1))

daisy$nComplete <- sapply(1:nrow(daisy), function(i) sum(daisy[i,3:13] == 2))
daisy$nAttempted <- sapply(1:nrow(daisy), function(i) sum(daisy[i,3:13] >= 1))

hist(daisy$nComplete)
hist(daisy$nAttempted)

ggplot(daisy, aes(x=GroupA, y=nComplete)) + geom_boxplot()
ggplot(daisy, aes(x=GroupA, y=nAttempted)) + geom_boxplot()

condCompare(daisy$nComplete, daisy$GroupA)
condCompare(daisy$nAttempted, daisy$GroupA)


poly <- read.csv("data/hs/polygon-grading.csv")

poly$GroupA <- poly$GroupA == "1"

poly$nComplete <- sapply(1:nrow(poly), function(i) sum(poly[i,3:9] == 2))
poly$nAttempted <- sapply(1:nrow(poly), function(i) sum(poly[i,3:9] >= 1))

poly$nComplete <- sapply(1:nrow(poly), function(i) sum(poly[i,3:9] == 2))
poly$nAttempted <- sapply(1:nrow(poly), function(i) sum(poly[i,3:9] >= 1))

hist(poly$nComplete)
hist(poly$nAttempted)

ggplot(poly, aes(x=GroupA, y=nComplete)) + geom_boxplot()
ggplot(poly, aes(x=GroupA, y=nAttempted)) + geom_boxplot()

condCompare(poly$nComplete, poly$GroupA)
condCompare(poly$nAttempted, poly$GroupA)
