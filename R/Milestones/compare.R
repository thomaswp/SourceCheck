library(readr)
library(plyr)
library(reshape2)

filterFeatures <- function(data) {
  for (traceID in unique(data$traceID)) {
    subset <- data[data$traceID == traceID,]
    for (i in 3:ncol(subset)) {
      data[data$traceID == traceID,i] <- filterFeature(subset[,i][[1]])
    }
  }
  data
}

filterFeature <- function(v) {
  # print(v)
  x <- c(v, 0) != c(0, v)
  x <- x[-length(x)]
  # print(as.numeric(x))
  for (i in 2:(length(x)-1)) {
    if (x[i] && x[i+1]) {
      x[i] <- F
      x[i + 1] <- F
      v[i] <- v[i-1]
    }
  }
  v
}

filterFeaturesWindow <- function(data, windowSize = 5) {
  for (traceID in unique(data$traceID)) {
    # For each trace
    # print(traceID)
    trace <- data[data$traceID == traceID,]
    for (f in 3:length(trace)) {
      # For each feature
      i <- 1
      
      while(i < (nrow(trace) - windowSize -1)) {
        value <- trace[,f][i]
        idx <- i
        while (idx <= nrow(trace) && value == trace[,f][idx]) {
          idx <- idx + 1
        }
        startChangeIdx <- idx
        value <- trace[,f][idx]
        # Move forword to find stable rows
        consistentIdx <- idx
        isConsistent <- FALSE
        while (consistentIdx < nrow(trace) && !isConsistent) {
          # look forward windowSize - 1 rows
          for (j in 1:(windowSize - 1)) {
            if (consistentIdx+j <= nrow(trace)) {
              # Unstable rows detected, set new Idx and move on
              if (trace[,f][consistentIdx+j] != value) {
                value <- trace[,f][consistentIdx+j]
                consistentIdx <- consistentIdx + j
                isConsistent <- FALSE
                if (consistentIdx == nrow(trace)) {
                  # Reach the last line
                  isConsistent = TRUE
                }
                break
              }
            }
          }
          # Stable rows are found
          if (j == windowSize - 1) {
            consistentIdx = consistentIdx + j
            if (consistentIdx > nrow(trace)) {
              consistentIdx = nrow(trace)
            }
            isConsistent = TRUE
            break
          }
        }
        if (isConsistent) {
          for (k in startChangeIdx:consistentIdx) {
            trace[,f][k] <- value
          }
        }
        i <- consistentIdx
      }
    }
    data[data$traceID == traceID,] <- trace
  }
  data
}

getStates <- function(data) {
  sapply(1:nrow(data), function(i) do.call(paste0, as.list(data[i, c(-1,-2)])))
}

equalsMatrix <- function(states) {
  size <- length(states)
  mat <- matrix(nrow=size, ncol=size)
  for (i in 1:size) {
    for (j in max(1,i-10):min(size,i+10)) {
      mat[i,j] <- states[i] == states[j]    
    }
  }
  mat
}

compareMats <- function(statesTut, statesAuto) {
  mat1 <- equalsMatrix(statesAuto)
  mat2 <- equalsMatrix(statesTut)
  mat3 <- mat1 * 2 + mat2
  plotMat(mat3)
}

plotMat <- function(mat) {
  melted <- melt(mat)
  melted <- melted[!is.na(melted$value),]
  melted$value <- factor(melted$value, levels=c("0", "1", "2", "3"))
  ggplot(melted, aes(x=Var1, y=Var2, color=value)) + geom_point() + 
    scale_color_manual(labels=c("0"="TN", "1"="FN", "2"="FP", "3"="TP"), values=c("0"="#ffffff", "1"="#ff0000", "2"="#0000ff", "3"="#ff00ff"))
}

compareMatsI <- function(tutor, auto, i) {
  traceID <- unique(tutor$traceID)[i]
  compareMats(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
}

confMatRand <- function(statesTut) {
  size <- length(statesTut)
  mat1 <- matrix(nrow=size, ncol=size)
  for (i in 1:size) {
    for (j in max(1,i-10):min(size,i+10)) {
      mat1[i,j] <- round(runif(1))
    }
  }
  mat2 <- equalsMatrix(statesTut)
  mat3 <- mat1 * 2 + mat2
  conf <- sapply(0:3, function(i) sum(mat3==i, na.rm=T))
  conf <- conf / sum(conf)
  names(conf) <- c("TN", "FN", "FP", "TP")
  frame <- data.frame(tn=conf["TN"], fn=conf["FN"], fp=conf["FP"], tp=conf["TP"])
  rownames(frame) <- c()
  frame
}

confMatUnif <- function(statesTut) {
  mat2 <- equalsMatrix(statesTut)
  mat3 <- 2 + mat2
  conf <- sapply(0:3, function(i) sum(mat3==i, na.rm=T))
  conf <- conf / sum(conf)
  names(conf) <- c("TN", "FN", "FP", "TP")
  frame <- data.frame(tn=conf["TN"], fn=conf["FN"], fp=conf["FP"], tp=conf["TP"])
  rownames(frame) <- c()
  frame
}

confMat <- function(statesTut, statesAuto) {
  mat1 <- equalsMatrix(statesAuto)
  mat2 <- equalsMatrix(statesTut)
  mat3 <- (mat1 * 2) + mat2
  conf <- sapply(0:3, function(i) sum(mat3==i, na.rm=T))
  conf <- conf / sum(conf)
  names(conf) <- c("TN", "FN", "FP", "TP")
  frame <- data.frame(tn=conf["TN"], fn=conf["FN"], fp=conf["FP"], tp=conf["TP"])
  rownames(frame) <- c()
  frame
}

confMatI <- function(tutor, auto, i) {
  traceID <- unique(tutor$traceID)[i]
  confMat(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
}

getStats <- function(tutor, auto) {
  stats <- data.frame(tn=numeric(0), fn=numeric(0), fp=numeric(0), tp=numeric(0))
  for (traceID in unique(auto$traceID)) {
    row <- confMat(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
    stats <- rbind(stats, row)
  }
  stats$prec <- stats$tp / (stats$tp + stats$fp)
  stats$rec <- stats$tp / (stats$tp + stats$fn)
  stats$f1 <- 2 / (1/stats$prec + 1/stats$rec)
  stats
}

getStatsRand <- function(tutor) {
  stats <- data.frame(tn=numeric(0), fn=numeric(0), fp=numeric(0), tp=numeric(0))
  for (traceID in unique(tutor$traceID)) {
    row <- confMatRand(tutor$state[tutor$traceID == traceID])
    stats <- rbind(stats, row)
  }
  stats$prec <- stats$tp / (stats$tp + stats$fp)
  stats$rec <- stats$tp / (stats$tp + stats$fn)
  stats$f1 <- 2 / (1/stats$prec + 1/stats$rec)
  stats
}

getStatsUnif <- function(tutor) {
  stats <- data.frame(tn=numeric(0), fn=numeric(0), fp=numeric(0), tp=numeric(0))
  for (traceID in unique(tutor$traceID)) {
    row <- confMatUnif(tutor$state[tutor$traceID == traceID])
    stats <- rbind(stats, row)
  }
  stats$prec <- stats$tp / (stats$tp + stats$fp)
  stats$rec <- stats$tp / (stats$tp + stats$fn)
  stats$f1 <- 2 / (1/stats$prec + 1/stats$rec)
  stats
}

fixData <- function(data) {
  data <- filterFeaturesWindow(data)
  data$traceID <- substr(data$traceID, 1, 8)
  data$state <- getStates(data)
  data
}

run <- function() {
  shapes <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-test.csv"))
  distance <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-distance.csv"))
  tutor <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-human.csv"))
  
  statsShapes <- getStats(tutor, shapes)
  mean(statsShapes$f1)
  mean(statsShapes$prec)
  mean(statsShapes$rec)
  
  statsDis <- getStats(tutor, distance)
  mean(statsDis$f1)
  mean(statsDis$prec)
  mean(statsDis$rec)
  
  statsRand <- getStatsRand(tutor)
  mean(statsRand$f1)
  mean(statsRand$prec)
  mean(statsRand$rec)
  
  statsUnif <- getStatsUnif(tutor)
  mean(statsUnif$f1)
  mean(statsUnif$prec)
  mean(statsUnif$rec)
}