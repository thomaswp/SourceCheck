
library(plyr)
library(ggplot2)

source("../Hints Comparison/util.R")

runme <- function() {
  logs <- read.csv("../../data/camp/campHS2018.csv", colClasses = c(rep(NA,9), "NULL"))
  logs$time <- strptime(logs$time, "%Y-%m-%d %H:%M:%S")
  
  daisyWECols <- 14:15
  daisyObjCols <- 16:24
  
  daisy <- read.csv("data/hs/daisy-grading.csv")
  daisy <- replaceTimes(daisy, logs, daisyWECols, daisyObjCols)
  daisy$GroupA <- daisy$GroupA == "1"
  
  plotProgress(daisy, daisyObjCols)
  plotMeanProgress(daisy, daisyObjCols)
  
  daisy$nComplete <- sapply(1:nrow(daisy), function(i) sum(!is.na(daisy[i,daisyObjCols])))
  daisy$nAttempted <- sapply(1:nrow(daisy), function(i) sum(daisy[i,5:13] >= 1))
  
  hist(daisy$nComplete)
  hist(daisy$nAttempted)
  
  ggplot(daisy, aes(x=GroupA, y=nComplete)) + geom_boxplot()
  ggplot(daisy, aes(x=GroupA, y=nAttempted)) + geom_boxplot()
  
  condCompare(daisy$nComplete, daisy$GroupA)
  condCompare(daisy$nAttempted, daisy$GroupA)
  
  
  polyWECols <- 10:11
  polyObjCols <- 12:16
  
  poly <- read.csv("data/hs/poly-grading.csv")
  poly <- replaceTimes(poly, logs, polyWECols, polyObjCols)
  poly$GroupA <- poly$GroupA == "1"
  
  plotProgress(poly, polyObjCols)
  plotMeanProgress(poly, polyObjCols)
  
  poly$nComplete <- sapply(1:nrow(poly), function(i) sum(!is.na(poly[i,polyObjCols])))
  poly$nAttempted <- sapply(1:nrow(poly), function(i) sum(poly[i,5:9] >= 1))
  
  hist(poly$nComplete)
  hist(poly$nAttempted)
  
  ggplot(poly, aes(x=GroupA, y=nComplete)) + geom_boxplot()
  ggplot(poly, aes(x=GroupA, y=nAttempted)) + geom_boxplot()
  
  condCompare(poly$nComplete, poly$GroupA)
  condCompare(poly$nAttempted, poly$GroupA)
  
  brickWECols <- c()
  brickObjCols <- 8:12
  
  brick <- read.csv("data/hs/brick-grading.csv")
  brick <- replaceTimes(brick, logs, brickWECols, brickObjCols)
  brick$GroupA <- brick$GroupA == "1"
  
  plotProgress(brick, brickObjCols)
  plotMeanProgress(brick, brickObjCols)
  
  brick$nComplete <- sapply(1:nrow(brick), function(i) sum(!is.na(brick[i,brickObjCols])))
  brick$nAttempted <- sapply(1:nrow(brick), function(i) sum(brick[i,5:9] >= 1))
  
  hist(brick$nComplete)
  hist(brick$nAttempted)
  
  ggplot(brick, aes(x=GroupA, y=nComplete)) + geom_boxplot()
  ggplot(brick, aes(x=GroupA, y=nAttempted)) + geom_boxplot()
  
  condCompare(brick$nComplete, brick$GroupA)
  condCompare(brick$nAttempted, brick$GroupA)
}

replaceTimes <- function(grades, logs, weCols, objCols) {
  allCols <- c(weCols, objCols)
  idMap <- data.frame(id=unique(unlist(grades[,allCols])))
  idMap <- merge(idMap, logs[1:3], all.y = T)
  missing <- idMap$id[!is.na(idMap$id) & is.na(idMap$time)]
  if (length(missing) > 0) {
    stop(paste('Missing IDs:', str(missing)))
  }
  idMap$time <- as.numeric(idMap$time)
  
  for (i in allCols) {
    tmp <- data.frame(id=grades[,i])
    tmp <- merge(tmp, idMap, all.x = T)
    if (sum(tmp$id == -1) != sum(is.na(tmp$time))) {
      print(tmp[,c("id", "time")])
      stop('Missing IDs')
    }
    grades[,i] <- tmp$time
  }
  grades
}

getProgress <- function(grades, objCols, divs=50) {
  start <- min(grades[,objCols], na.rm = T)
  end <- max(grades[,objCols], na.rm = T)
  
  progress <- NULL
  for (i in 0:divs) {
    time <- floor((end - start) * i / divs + start)
    for (j in 1:nrow(grades)) {
      id <- grades[j,1]
      group <- grades$GroupA[j]
      comp <- sum(grades[j,objCols] <= time, na.rm = T)
      progress <- rbind(progress, data.frame(id=id, group=group, time=time-start, comp=comp))
    }
  }
  progress
}

plotProgress <- function(grades, objCols) {
  progress <- getProgress(grades, objCols)
  ggplot(progress, aes(x=as.ordered(time),y=comp,fill=group)) + geom_boxplot()
}

plotMeanProgress <- function(grades, objCols) {
  progress <- getProgress(grades, objCols)
  mProgress <- ddply(progress, c("group", "time"), summarize, mComp=mean(comp))
  ggplot(mProgress, aes(x=time,y=mComp,group=group,color=group)) + geom_line() + geom_vline(xintercept = 45*60)
}
