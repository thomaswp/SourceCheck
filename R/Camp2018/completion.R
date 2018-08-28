
library(plyr)
library(ggplot2)
library(survival)
library(survminer)

source("../Hints Comparison/util.R")

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))
tr <- function(x) ifelse(is.na(x), F, x)
range1 <- function(x) max(x, na.rm=T) - min(x, na.rm=T)
min.d <- function(x, default) if (length(x) == 0) default else min(x)

runme <- function() {
  logs <- read.csv("../../data/camp/campHS2018.csv", colClasses = c(rep(NA,9), "NULL"))
  logs$time <- strptime(logs$time, "%Y-%m-%d %H:%M:%S")
  
  # Load Data
  
  daisyWECols <- 14:15
  daisyObjCols <- 16:24
  
  daisy <- read.csv("data/hs/daisy-grading.csv")
  daisy$assignmentID <- "DaisyDesign"
  daisy <- replaceTimes(daisy, logs, daisyWECols, daisyObjCols)
  daisy$GroupA <- daisy$GroupA == "1"
  
  daisyStart <- as.numeric(strptime("2018-07-23 13:09:00", "%Y-%m-%d %H:%M:%S"))
  daisyEnd <- as.numeric(strptime("2018-07-23 13:55:00", "%Y-%m-%d %H:%M:%S"))
  # manually check that the start time is valid (investigate outliers)
  # boxplot(daisy$startTime - daisyStart)
  # then use the same start time for everyone
  daisy$startTime <- daisyStart
  daisy$duration <- daisy$endTime - daisy$startTime
  # manually check any particularly short-druation ones
  # daisy[order(daisy$duration), c(1,32)]
  
  polyWECols <- 10:11
  polyObjCols <- 12:16
  
  poly <- read.csv("data/hs/poly-grading.csv")
  poly$assignmentID = "SpiralPolygon"
  poly <- replaceTimes(poly, logs, polyWECols, polyObjCols)
  poly$GroupA <- poly$GroupA == "1"
  
  polyStart <- as.numeric(strptime("2018-07-24 9:04:00", "%Y-%m-%d %H:%M:%S"))
  polyEnd <- as.numeric(strptime("2018-07-24 9:50:00", "%Y-%m-%d %H:%M:%S"))
  # boxplot(poly$startTime - polyStart)
  poly$startTime <- polyStart
  poly$duration <- poly$endTime - poly$startTime
  # poly[order(poly$duration), c(1,21)]
  
  brickWECols <- c()
  brickObjCols <- 8:12
  
  brick <- read.csv("data/hs/brick-grading.csv")
  brick$assignmentID <- "BrickWall"
  brick <- replaceTimes(brick, logs, brickWECols, brickObjCols)
  brick$GroupA <- brick$GroupA == "1"
  
  brickStart <- as.numeric(strptime("2018-07-24 10:15:00", "%Y-%m-%d %H:%M:%S"))
  brickEnd <- as.numeric(strptime("2018-07-24 11:00:00", "%Y-%m-%d %H:%M:%S"))
  # boxplot(brick$startTime - brickStart)
  brick$startTime <- brickStart
  brick$duration <- brick$endTime - brick$startTime
  # brick[order(brick$duration), c(1,18)]
  
  # Plot progress
  
  dProg <- getProgress(daisy, daisyObjCols)
  pProg <- getProgress(poly, polyObjCols)
  bProg <- getProgress(brick, brickObjCols)
  allProg <- rbind(dProg, pProg, bProg)
  
  plotMeanProgress(dProg, daisyEnd - daisyStart)
  plotMeanProgress(pProg, polyEnd - polyStart)
  plotMeanProgress(bProg, brickEnd - brickStart)
  
  
  daisyPostWE <- daisy
  daisyPostWEProg <- getProgress(daisyPostWE, 18:24)
  daisyPostWE$startTime <- daisyPostWE$RowID4.Draw.a.daisy.design
  plotMeanProgress(daisyPostWEProg, daisyEnd - daisyStart)
  daisyDuration <- daisyEnd - daisyStart
  survivalPlot(daisyPostWE, daisyDuration, 3)
  
  polyPostWE <- poly
  polyPostWE$startTime <- polyPostWE$RowID3.Draw.a.squiral..WE.step.
  plotMeanProgress(getProgress(polyPostWE, 13:16), polyEnd - polyStart)
  
  
  # Daisy Comps
  
  daisy$nComplete <- sapply(1:nrow(daisy), function(i) sum(tr(daisy[i,daisyObjCols] <= daisyEnd)))
  daisy$nAttempted <- sapply(1:nrow(daisy), function(i) sum(daisy[i,5:13] >= 1))
  
  hist(daisy$nComplete)
  ggplot(daisy, aes(x=GroupA, y=nComplete)) + geom_violin() + geom_boxplot(width=0.2)
  
  condCompare(daisy$nComplete, daisy$GroupA)
  condCompare(daisy$nAttempted, daisy$GroupA)
  
  # Sig diff at t=15
  condCompare(dProg$comp, dProg$group, filter=dProg$time == 15*60)
  
  #Poly Comps
  
  poly$nComplete <- sapply(1:nrow(poly), function(i) sum(tr(poly[i,polyObjCols] <= polyEnd)))
  poly$nAttempted <- sapply(1:nrow(poly), function(i) sum(poly[i,5:9] >= 1))
  
  hist(poly$nComplete)
  
  ggplot(poly, aes(x=GroupA, y=nComplete)) + geom_violin() + geom_boxplot(width=0.2)
  
  condCompare(poly$nComplete, poly$GroupA)
  
  # Sig diff at t=20
  condCompare(pProg$comp, pProg$group, filter=pProg$time == 20*60)
  
  # Brick Comps
  
  brick$nComplete <- sapply(1:nrow(brick), function(i) sum(tr(brick[i,brickObjCols] <= brickEnd)))
  brick$nAttempted <- sapply(1:nrow(brick), function(i) sum(brick[i,5:9] >= 1))
  
  hist(brick$nComplete)
  
  ggplot(brick, aes(x=GroupA, y=nComplete)) + geom_violin() + geom_boxplot(width=0.2)
  
  condCompare(brick$nComplete, brick$GroupA)
  
  condCompare(bProg$comp, bProg$group, filter=bProg$time == 35*60)
}

survivalPlot <- function(progress, duration, n) {
  progress$duration <- duration
  progress <- progress[progress$realTime <= duration,]
  valid <- ddply(progress,
                  c("id", "group", "duration"), summarize,
                  maxTime = duration - min(realTime),
                  time = min.d(time[comp >= n], maxTime), 
                  event = time!=maxTime)
  surv <- Surv(time=valid$time, event=valid$event)
  fit <- survfit(surv ~ group, data=valid)
  do.call("ggsurvplot", list(fit, pval=T, data=valid))
  #ggsurvplot(fit, pval=T, data=valid)
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
  
  names(grades)[1] <- "projectID"
  grades$startTime <- grades$endTime <- NA
  for (i in 1:nrow(grades)) {
    assignmentID <- grades$assignmentID[i]
    projectID <- as.character(grades$projectID[i])
    times <- logs[(logs$assignmentID == assignmentID | logs$assignmentID == "demo") & 
                   logs$projectID == projectID, "time"]
    start <- as.numeric(min(times))
    grades$startTime[i] <- start
    grades$endTime[i] <- as.numeric(max(times[times < start + 60 * 60]))
  }
  grades
}

getProgress <- function(grades, objCols) {
  start <- min(grades$startTime, na.rm=T)
  end <- max(grades$duration)
  
  inc <- 50
  time <- 0
  
  progress <- NULL
  while (time < end + inc) {
    for (j in 1:nrow(grades)) {
      id <- grades[j,1]
      group <- grades$GroupA[j]
      assignmentID <- grades$assignmentID[j]
      startTime <- grades$startTime[j]
      comp <- sum(grades[j,objCols] <= startTime + time, na.rm = T)
      perc <- comp / length(objCols)
      progress <- rbind(progress, data.frame(id=id, group=group, assignmentID=assignmentID, 
                                             time=time, realTime=time+startTime-start, comp=comp, perc=perc))
    }
    time <- time + inc
  }
  progress
}

plotProgress <- function(progress) {
  ggplot(progress, aes(x=as.ordered(time),y=perc,fill=group)) + geom_boxplot() + theme_bw()
}

plotMeanProgress <- function(progress, cutoff = 45*60) {
  progress$working <- tr(progress$realTime < cutoff)
  mProgress <- ddply(progress, c("group", "time"), summarize, 
                     mPerc = mean(perc[working]), sePerc = se(perc[working]), pWorking=mean(working))
  mProgress <- mProgress[mProgress$time <= cutoff,]
  ggplot(mProgress) + 
    geom_line(aes(x=time,y=mPerc,group=group,color=group), size=1) + 
    geom_line(aes(x=time,y=pWorking,group=group,color=group), linetype=2, size=1) + 
    geom_ribbon(aes(ymin=mPerc-sePerc, ymax=mPerc+sePerc, x=time, fill=group), alpha=0.3, color="gray") +
    #geom_ribbon(aes(ymin=mPerc-sePerc*1.96, ymax=mPerc+sePerc*1.96, x=time, fill=group), alpha=0.15, color="NA") + 
    theme_bw()
}
