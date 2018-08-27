
library(plyr)
library(ggplot2)

source("../Hints Comparison/util.R")

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))

runme <- function() {
  logs <- read.csv("../../data/camp/campHS2018.csv", colClasses = c(rep(NA,9), "NULL"))
  logs$time <- strptime(logs$time, "%Y-%m-%d %H:%M:%S")
  
  # Load Data
  
  daisyWECols <- 14:15
  daisyObjCols <- 16:24
  
  daisy <- read.csv("data/hs/daisy-grading.csv")
  daisy <- replaceTimes(daisy, logs, daisyWECols, daisyObjCols)
  daisy$GroupA <- daisy$GroupA == "1"
  daisy$assignment <- "daisy"
  
  daisyStart <- as.numeric(strptime("2018-07-23 13:09:00", "%Y-%m-%d %H:%M:%S"))
  
  polyWECols <- 10:11
  polyObjCols <- 12:16
  
  poly <- read.csv("data/hs/poly-grading.csv")
  poly <- replaceTimes(poly, logs, polyWECols, polyObjCols)
  poly$GroupA <- poly$GroupA == "1"
  poly$assignment = "poly"
  
  brickWECols <- c()
  brickObjCols <- 8:12
  
  brick <- read.csv("data/hs/brick-grading.csv")
  brick <- replaceTimes(brick, logs, brickWECols, brickObjCols)
  brick$GroupA <- brick$GroupA == "1"
  brick$assignment <- "brick"
  
  # Plot progress
  
  # TODO: Need to provide a start time, since min(time) will be the first obj finished
  dProg <- getProgress(daisy, daisyObjCols)
  pProg <- getProgress(poly, polyObjCols)
  bProg <- getProgress(brick, brickObjCols)
  allProg <- rbind(dProg, pProg, bProg)
  
  plotMeanProgress(dProg)
  plotMeanProgress(pProg)
  plotMeanProgress(bProg)
  
  # TODO: count accomplished in each third, see if there's a relationship btw condition and time
  end <- 45 * 60
  snapshots <- allProg[allProg$time %in% c(0, end/2, end),]
  
  # Daisy Comps
  
  daisy$nComplete <- sapply(1:nrow(daisy), function(i) sum(!is.na(daisy[i,daisyObjCols])))
  daisy$nAttempted <- sapply(1:nrow(daisy), function(i) sum(daisy[i,5:13] >= 1))
  
  hist(daisy$nComplete)
  hist(daisy$nAttempted)
  
  ggplot(daisy, aes(x=GroupA, y=nComplete)) + geom_boxplot()
  ggplot(daisy, aes(x=GroupA, y=nAttempted)) + geom_boxplot()
  
  condCompare(daisy$nComplete, daisy$GroupA)
  condCompare(daisy$nAttempted, daisy$GroupA)
  
  #Poly Comps
  
  poly$nComplete <- sapply(1:nrow(poly), function(i) sum(!is.na(poly[i,polyObjCols])))
  poly$nAttempted <- sapply(1:nrow(poly), function(i) sum(poly[i,5:9] >= 1))
  
  hist(poly$nComplete)
  hist(poly$nAttempted)
  
  ggplot(poly, aes(x=GroupA, y=nComplete)) + geom_boxplot()
  ggplot(poly, aes(x=GroupA, y=nAttempted)) + geom_boxplot()
  
  condCompare(poly$nComplete, poly$GroupA)
  condCompare(poly$nAttempted, poly$GroupA)
  
  # Brick Comps
  
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
  idMap <- merge(idMap, logs[1:3], all.x = T)
  missing <- idMap$id[!is.na(idMap$id) & is.na(idMap$time)]
  if (sum(missing != -1) > 0) {
    stop(paste('Missing IDs:', str(missing)))
  }
  idMap$time <- as.numeric(idMap$time)
  
  for (i in allCols) {
    tmp <- data.frame(id=grades[,i])
    tmp <- join(tmp, idMap, by="id", type="left")
    if (sum(tmp$id == -1) != sum(is.na(tmp$time))) {
      print(tmp[,c("id", "time")])
      stop('Missing IDs')
    }
    grades[,i] <- tmp$time
  }
  grades
}

getProgress <- function(grades, objCols) {
  start <- min(grades[,objCols], na.rm = T)
  end <- max(grades[,objCols], na.rm = T)
  
  inc <- 50
  time <- start
  
  progress <- NULL
  while (time < end + inc) {
    for (j in 1:nrow(grades)) {
      id <- grades[j,1]
      group <- grades$GroupA[j]
      assignment <- grades$assignment[j]
      comp <- sum(grades[j,objCols] <= time, na.rm = T)
      perc <- comp / length(objCols)
      progress <- rbind(progress, data.frame(id=id, group=group, assignment=assignment, 
                                             time=time-start, comp=comp, perc=perc))
    }
    time <- time + inc
  }
  progress
}

plotProgress <- function(progress) {
  ggplot(progress, aes(x=as.ordered(time),y=perc,fill=group)) + geom_boxplot() + theme_bw()
}

plotMeanProgress <- function(progress) {
  mProgress <- ddply(progress, c("group", "time"), summarize, mPerc = mean(perc), sePerc = se(perc))
  ggplot(mProgress, aes(x=time,y=mPerc,group=group,color=group)) + 
    geom_line() + geom_ribbon(aes(ymin=mPerc-sePerc, ymax=mPerc+sePerc, x=time, fill=group), alpha=0.5, color="gray") + 
    geom_vline(xintercept = 45*60) + theme_bw()
}
