library(readr)
library(plyr)
library(reshape2)

first <- function(x) head(x, 1)

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
      while(i < (nrow(trace) - windowSize)) {
        value <- trace[,f][i]
        idx <- i
        # Find the changed row
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
          cntRows <- 0
          for (j in 1:(windowSize - 1)) {
            # Reach the last line
            if (consistentIdx + j == nrow(trace)) {
              value <- trace[,f][nrow(trace)]
              consistentIdx <- nrow(trace)
              isConsistent <- TRUE
              break
            }
            # Unstable rows detected, set new Idx and move on
            if (trace[,f][consistentIdx + j] != value) {
              value <- trace[,f][consistentIdx + j]
              consistentIdx <- consistentIdx + j
              isConsistent <- FALSE
              break
            } else {
              cntRows <- cntRows + 1
            }
          }
          # Stable rows are found
          if (cntRows == windowSize - 1) {
            consistentIdx = consistentIdx + cntRows
            isConsistent = TRUE
          }
        }
        
        # Overwrite the values
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
    for (j in max(1,i-win):min(size,i+win)) {
      if (i != j) mat[i,j] <- states[i] == states[j]    
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
    for (j in max(1,i-win):min(size,i+win)) {
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
  confMatFromOutcomes(mat3)
}

confMatFromOutcomes <- function(outcomes) {
  conf <- sapply(0:3, function(i) sum(outcomes==i, na.rm=T))
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

getStats <- function(tutor, f) {
  traceIDs <- unique(tutor$traceID)
  stats <- data.frame(tn=numeric(0), fn=numeric(0), fp=numeric(0), tp=numeric(0))
  for (traceID in traceIDs) {
    row <- f(traceID)
    stats <- rbind(stats, row)
  }
  addStats(stats)
}

addStats <- function(stats) {
  stats$prec <- stats$tp / (stats$tp + stats$fp)
  stats$rec <- stats$tp / (stats$tp + stats$fn)
  stats$f1 <- 2 / (1/stats$prec + 1/stats$rec)
  stats$acc <- stats$tp + stats$tn
  stats$v0 <- stats$tn + stats$fn
  stats$v1 <- stats$tp + stats$fp
  h0 <- stats$tn + stats$fp
  h1 <- stats$tp + stats$fn
  stats$chance <- stats$v0 * h0 + stats$v1 * h1
  stats$kappa <- (stats$acc - stats$chance) / (1 - stats$chance)
  stats
}

getStatsComp <- function(tutor, auto) {
  getStats(tutor, function(traceID) {
    confMat(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
  })
}

getStatsRand <- function(tutor) {
  getStats(tutor, function(traceID) {
    confMatRand(tutor$state[tutor$traceID == traceID])
  })
}

getStatsUnif <- function(tutor) {
  stats <- data.frame(tn=numeric(0), fn=numeric(0), fp=numeric(0), tp=numeric(0))
  getStats(tutor, function(traceID) {
    confMatUnif(tutor$state[tutor$traceID == traceID])
  })
}

fixData <- function(data, filter=T) {
  if (filter) data <- filterFeaturesWindow(data)
  data$traceID <- substr(data$traceID, 1, 8)
  data$state <- getStates(data)
  data <- data[order(data$traceID, data$RowID),]
  data
}

extractPairs <- function(tutor) {
  set.seed(1234)
  pairs <- data.frame(rowA=numeric(0), rowB=numeric(0), same=logical(0), state=character(0))
  sample1 <- function(x) if (length(x) == 1) x else sample(x, 1)
  for (state in unique(tutor$state)) {
    hasState <- tutor[tutor$state == state,]
    hasntState <- tutor[tutor$state != state,]
    traces <- unique(hasState$traceID)
    tracePairs <- data.frame(traceA=character(0), traceB=character(0))
    for (traceA in traces) {
      for (traceB in traces) {
        tracePairs <- rbind(tracePairs, data.frame(traceA=traceA, traceB=traceB))
      }
    }
    maxRows <- 10
    if (nrow(tracePairs) > maxRows) {
      tracePairs <- tracePairs[sample(nrow(tracePairs), maxRows, F),] 
    }
    for (i in 1:nrow(tracePairs)) {
      traceA <- tracePairs$traceA[i]
      traceB <- tracePairs$traceB[i]
      
      rowA <- sample1(hasState$RowID[hasState$traceID == traceA])
      rowB <- sample1(hasState$RowID[hasState$traceID == traceB])
      pairs <- rbind(pairs, data.frame("rowA"=rowA, "rowB"=rowB, "same"=T, "state"=state))
      
      rowA <- sample1(hasntState$RowID[hasntState$traceID == traceA])
      rowB <- sample1(hasntState$RowID[hasntState$traceID == traceB])
      pairs <- rbind(pairs, data.frame("rowA"=rowA, "rowB"=rowB, "same"=F, "state"=state))
    }
  }  
  pairs
}

evalPairs <- function(pairs, auto) {
  pairs$pred <- sapply(1:nrow(pairs), function(i) {
    rowA <- pairs$rowA[i]
    rowB <- pairs$rowB[i]
    eq <- auto$state[auto$RowID==rowA] == auto$state[auto$RowID==rowB]
    if (length(eq) != 1) print(paste(length(eq), rowA, rowB))
    eq[1]
  })
  pairs$outcome <- pairs$same + 2 * pairs$pred
  conf <- confMatFromOutcomes(pairs$outcome)
  addStats(conf)
}

extractRandomPairs <- function(tutor) {
  set.seed(1234)
  pairs <- data.frame(rowA=numeric(0), rowB=numeric(0), distance=numeric(0))
  traces <- unique(tutor$traceID)
  for (i in 1:(length(traces)-1)) {
    rowsA <- tutor[tutor$traceID==traces[[i]],]
    nRowsA <- nrow(rowsA)
    for (j in (i+1):length(traces)) {
      rowsB <- tutor[tutor$traceID==traces[[j]],]
      nRowsB <- nrow(rowsB)
      
      nComb <- nRowsA * nRowsB
      combos <- sample(nComb, 25, F)
      
      for (combo in combos) {
        # Deal with 1-based-indexing
        ia <- (combo-1) %% nRowsA + 1
        ib <- (combo-1) %/% nRowsA + 1
        
        rowA <- rowsA$RowID[ia]
        stateA <- rowsA$state[ia]
        rowB <- rowsB$RowID[ib]
        stateB <- rowsB$state[ib]
        
        distance <- mean(strsplit(stateA, "")[[1]] != strsplit(stateB, "")[[1]])
        pairs <- rbind(pairs, data.frame(rowA=rowA, rowB=rowB, distance=distance))
      }
    }
  }
  pairs
}

evalRandomPairs <- function(pairs, auto) {
  pairs$predDis <- sapply(1:nrow(pairs), function(i) {
    rowA <- pairs$rowA[i]
    rowB <- pairs$rowB[i]
    stateA <- auto$state[auto$RowID==rowA]
    stateB <- auto$state[auto$RowID==rowB]
    mean(strsplit(stateA, "")[[1]] != strsplit(stateB, "")[[1]])
  })
  pairs
  cor(pairs$distance, pairs$predDis, method="spearman")
}

featureMat <- function(tutor, auto) {
  mat <- matrix(nrow=ncol(tutor) - 3, ncol=ncol(auto) - 3)
  for (i in 3:(ncol(tutor) - 1)){
    for (j in 3:(ncol(auto) - 1)){
      mat[i-2,j-2] <- jacc(tutor[,i], auto[,j])
    } 
  }
  mat
}

dependMat <- function(tutor, auto) {
  mat <- matrix(nrow=ncol(tutor) - 3, ncol=ncol(auto) - 3)
  for (i in 3:(ncol(tutor) - 1)){
    for (j in 3:(ncol(auto) - 1)){
      mat[i-2,j-2] <- sum(tutor[,i] & auto[,j]) / sum(tutor[,i])
    } 
  }
  mat
}

featureProbs <- function(rows, f) {
  changes <- ddply(shapes11F, "traceID", summarize, changeIdx=first(which(F2==1)), changeRow = RowID[changeIdx], changeState=state[changeIdx])
  preMat <- (do.call("rbind", strsplit(changes$changeState, "")) == "1") + 0
  weights <- (colSums(preMat) / nrow(preMat)) ^ 2
  weights <- weights / sum(weights)
  weights
  scores <- 1 - (1 - preMat) %*% weights
  cbind(preMat, scores)
}

win <- 25

run <- function() {
  shapes11 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-shapes-S16S17-11.csv"))
  shapes1 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-shapes-S16S17-01.csv"))
  shapes4 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-shapes-S16S17-04.csv"))
  shapesAll <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-shapes-S16S17-45.csv"))
  # shapesND9 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-shapes-S16S17-09-ND.csv"))
  distance41 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-distance-S16S17-41.csv"))
  distance38 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-distance-S16S17-38.csv"))
  distance20 <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-distance-S16S17-20.csv"))
  tutor <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-human.csv"))
  
  statsShapes <- getStatsComp(tutor, shapes11)
  round(colwise(median)(statsShapes), 3)
  round(colwise(mean)(statsShapes), 3)
  round(colwise(IQR)(statsShapes), 3)
  
  round(colwise(mean)(getStatsComp(tutor, shapes1)), 3)
  round(colwise(mean)(getStatsComp(tutor, shapesAll)), 3)
  
  statsDis <- getStatsComp(tutor, distance38)
  round(colwise(median)(statsDis), 3)
  round(colwise(mean)(statsDis), 3)
  round(colwise(IQR)(statsDis), 3)
  round(colwise(mean)(getStatsComp(tutor, distance20)), 3)
  
  for (i in c(5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 200, 500)) {
    v <- win
    win <<- i
    statsShapes <- getStatsComp(tutor, shapes11)
    statsDis <- getStatsComp(tutor, distance38)
    print(paste(i, median(statsShapes$kappa) - median(statsDis$kappa), median(statsShapes$kappa), median(statsDis$kappa)))
    win <<- v
  }
  
  wilcox.test(statsShapes$kappa, statsDis$kappa, pair=T)
  
  statsRand <- getStatsRand(tutor)
  round(colwise(mean)(statsRand), 3)
  
  statsUnif <- getStatsUnif(tutor)
  round(colwise(mean)(statsUnif), 3)
  
  pairs <- extractPairs(tutor)
  evalPairs(pairs, shapes11)
  evalPairs(pairs, shapesAll)
  evalPairs(pairs, distance20)
  evalPairs(pairs, distance40)
  
  rPairs <- extractRandomPairs(tutor)
  evalRandomPairs(rPairs, shapes11)
  evalRandomPairs(rPairs, shapes1)
  evalRandomPairs(rPairs, shapesAll)
  
  tutorNoFilter <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-human.csv"), F)
  rPairsNF <- extractRandomPairs(tutorNoFilter)
  write.csv(rPairsNF, "../../data/csc200/all/analysis/squiralHW/rpairs.csv")
  # Run java 
  rPairsDis <- read.csv("../../data/csc200/all/analysis/squiralHW/rpairs-dis.csv")
  cor(rPairsDis$distance, rPairsDis$predDisTED)
  cor(rPairsDis$distance, rPairsDis$predDisSED)
  
  shapes11F <- fixData(read.csv("../../data/csc200/all/analysis/squiralHW/feature-shapes-F16-11.csv"))
}