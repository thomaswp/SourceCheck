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

getStates <- function(data) {
  sapply(1:nrow(data), function(i) do.call(paste0, data[i, c(-1,-2)]))
}

equalsMatrix <- function(states) {
  size <- length(states)
  mat <- matrix(nrow=size, ncol=size)
  for (i in 1:size) {
    for (j in 1:size) {
      mat[i,j] <- states[i] == states[j]    
    }
  }
  mat
}

compareMats <- function(statesTut, statesAuto) {
  mat1 <- equalsMatrix(statesAuto)
  mat2 <- equalsMatrix(statesTut)
  mat3 <- mat1 * 2 + mat2
  ggplot(melt(mat3), aes(x=Var1, y=Var2, color=as.factor(value))) + geom_point() + 
    scale_color_manual(labels=c("TN", "FN", "FP", "TP"), values=c("#ffffff", "#ff0000", "#0000ff", "#ff00ff"))
}

compareMatsI <- function(auto, tutor, i) {
  traceID <- unique(auto$traceID)[i]
  compareMats(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
}

confMat <- function(statesTut, statesAuto) {
  mat1 <- equalsMatrix(statesAuto)
  mat2 <- equalsMatrix(statesTut)
  mat3 <- (mat1 * 2) + mat2
  conf <- table(mat3)
  conf <- conf / sum(conf)
  names(conf) <- c("TN", "FN", "FP", "TP")
  frame <- data.frame(tn=conf["TN"], fn=conf["FN"], fp=conf["FP"], tp=conf["TP"])
  rownames(frame) <- c()
  frame
}

confMatI <- function(auto, tutor, i) {
  traceID <- unique(auto$traceID)[i]
  confMat(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
}

run <- function() {
  auto <- read_csv("../../data/csc200/all/analysis/squiralHW/feature-test.csv")
  tutor <- read_csv("../../data/csc200/all/analysis/squiralHW/feature-human.csv")
  
  auto <- filterFeatures(auto)
  tutor <- filterFeatures(tutor)
  
  auto$traceID <- substr(auto$traceID, 1, 8)
  tutor$traceID <- substr(tutor$traceID, 1, 8)
  
  auto$state <- getStates(auto)
  tutor$state <- getStates(tutor)
  
  stats <- data.frame(tn=numeric(0), fn=numeric(0), fp=numeric(0), tp=numeric(0))
  for (traceID in unique(auto$traceID)) {
    row <- confMat(tutor$state[tutor$traceID == traceID], auto$state[auto$traceID == traceID])
    stats <- rbind(stats, row)
  }
  stats$prec <- stats$tp / (stats$tp + stats$fp)
  stats$rec <- stats$tp / (stats$tp + stats$fn)
  stats$f1 <- 2 / (1/stats$prec + 1/stats$rec)
  stats
  
  mean(stats$f1)
  mean(stats$prec)
  mean(stats$rec)
}