
library(plyr)
library(ggplot2)

source("../Hints Comparison/util.R")

logs <- read.csv("../../data/camp/campHS2018.csv", colClasses = c(rep(NA,9), "NULL"))
logs$time <- strptime(logs$time, "%Y-%m-%d %H:%M:%S")

daisy <- read.csv("data/hs/daisy-grading.csv")

daisy$GroupA <- daisy$GroupA == "1"

idMap <- data.frame(id=unique(unlist(daisy[,14:24])))
idMap <- merge(idMap, logs[1:3])
idMap$time <- as.numeric(idMap$time)

for (i in 14:24) {
  tmp <- data.frame(id=daisy[,i])
  tmp <- merge(tmp, idMap, all.x = T)
  daisy[,i] <- tmp$time
}

start <- min(daisy[,14:24], na.rm = T)
end <- max(daisy[,14:24], na.rm = T)

progress <- NULL
for (i in 0:50) {
  time <- (end - start) * i / 100 + start
  for (j in 1:nrow(daisy)) {
    id <- daisy[j,1]
    group <- daisy$GroupA[j]
    comp <- sum(daisy[j,16:24] <= time, na.rm = T)
    progress <- rbind(progress, data.frame(id=id, group=group, time=time-start, comp=comp))
  }
}

ggplot(progress, aes(x=as.ordered(time),y=comp,fill=group)) + geom_boxplot()

mProgress <- ddply(progress, c("group", "time"), summarize, mComp=mean(comp))
ggplot(mProgress, aes(x=as.ordered(time),y=mComp,group=group,color=group)) + geom_line()


daisy$nComplete <- sapply(1:nrow(daisy), function(i) sum(!is.na(daisy[i,16:24])))
daisy$nAttempted <- sapply(1:nrow(daisy), function(i) sum(daisy[i,5:13] >= 1))

hist(daisy$nComplete)
hist(daisy$nAttempted)

ggplot(daisy, aes(x=GroupA, y=nComplete)) + geom_boxplot()
ggplot(daisy, aes(x=GroupA, y=nAttempted)) + geom_boxplot()

condCompare(daisy$nComplete, daisy$GroupA)
condCompare(daisy$nAttempted, daisy$GroupA)


poly <- read.csv("data/hs/polygon-grading.csv")

poly$GroupA <- poly$GroupA == "1"

idMap <- data.frame(id=unique(unlist(poly[,10:16])))
idMap <- merge(idMap, logs[1:3])
idMap$time <- as.numeric(idMap$time)

for (i in 10:16) {
  tmp <- data.frame(id=poly[,i])
  tmp <- merge(tmp, idMap, all.x = T)
  poly[,i] <- tmp$time
}

start <- min(poly[,10:16], na.rm = T)
end <- max(poly[,10:16], na.rm = T)

progress <- NULL
for (i in 0:50) {
  time <- (end - start) * i / 100 + start
  for (j in 1:nrow(poly)) {
    id <- poly[j,1]
    group <- poly$GroupA[j]
    comp <- sum(poly[j,12:16] <= time, na.rm = T)
    progress <- rbind(progress, data.frame(id=id, group=group, time=time-start, comp=comp))
  }
}


poly$nComplete <- sapply(1:nrow(poly), function(i) sum(!is.na(poly[i,12:16])))
poly$nAttempted <- sapply(1:nrow(poly), function(i) sum(poly[i,5:9] >= 1))


ggplot(progress, aes(x=as.ordered(time),y=comp,fill=group)) + geom_boxplot()

mProgress <- ddply(progress, c("group", "time"), summarize, mComp=mean(comp))
ggplot(mProgress, aes(x=as.ordered(time),y=mComp,group=group,color=group)) + geom_line()


hist(poly$nComplete)
hist(poly$nAttempted)

ggplot(poly, aes(x=GroupA, y=nComplete)) + geom_boxplot()
ggplot(poly, aes(x=GroupA, y=nAttempted)) + geom_boxplot()

condCompare(poly$nComplete, poly$GroupA)
condCompare(poly$nAttempted, poly$GroupA)
