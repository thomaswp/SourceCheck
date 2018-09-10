library(readr)
library(ggplot2)
library(rlist)
library(cluster)
library(tsne)
library(factoextra)

folder <- "../../data/csc200/all/analysis/guess1Lab/"

oldAnalysis <- function() {
  
  disMat <- as.matrix(read_csv(paste0(folder, "teds.csv"), col_names = FALSE))
  samples <-read_csv(paste0(folder, "samples.csv"))
  
  smallDisMat <- disMat[sample(nrow(samples), 250, F), sample(nrow(samples), 250, F)]
  sil <- sapply(2:50, function(i) pam(disMat, i, diss=T)$silinfo$avg.width)
  plot(unlist(sil))
  
  # 38 expert-authored states
  clusters <- pam(disMat, 38, diss=T)
  samples$cluster <- as.factor(clusters$clustering)
  samples$name <- paste0("X", samples$id + 1)
  samples$medoid <- samples$name %in% clusters$medoids
  write.csv(samples, paste0(folder, "samples-clustered.csv"))
  
  embed <- tsne(disMat, k=2, max_iter = 200, epoch=100)
  samples$x <- embed[,1]
  samples$y <- embed[,2]
  ggplot(samples, aes(x=x, y=y, color=cluster, shape=medoid)) + geom_point(size=3)
}

timingAnalysis <- function() {
  timings <- as.matrix(read_csv(paste0(folder, "feature-timing.csv"), col_names = FALSE))
  fviz_nbclust(timings, pam, method = "silhouette", k.max=nrow(timings) - 1)
  clusters <- pam(timings, 5, diss=T)
  
  features <- read_csv(paste0(folder, "features.csv"), col_names = TRUE)
  features$cluster <- clusters$clustering
  
  features <- features[order(features$cluster),]
  lastCluster <- 1
  for (i in 1:nrow(features)) {
    cluster <- features$cluster[i]
    if (cluster != lastCluster) cat("\n")
    lastCluster <- cluster
    cat(sprintf("%02d %02d: %s\n", features$cluster[i], features$id[i], features$name[i]))
  }
  
  
  features$name <- paste0("X", features$id + 1)
  features$medoid <- features$name %in% clusters$medoids
  
  embed <- tsne(timings, k=2, max_iter = 1000, epoch=250)
  features$x <- embed[,1]
  features$y <- embed[,2]
  ggplot(features, aes(x=x, y=y, color=as.factor(cluster), shape=medoid)) + geom_point(size=3)
}

jacc <- function(x, y) sum(x & y) / sum(x | y)

makeJaccMat <- function(rules) {
  size <- ncol(rules)
  mat <- matrix(nrow=size, ncol=size)
  for (i in 1:size) for (j in 1:size) mat[i,j] = if(i==j) 0 else jacc(rules[,i], rules[,j])
  return (mat)
}

calcScore <- function(rules) {
  count <- 1
  lastState <- rules[1,]
  for (i in 2:nrow(rules)) {
    state <- rules[i,]
    if (mean(state == lastState) < 1) count <- count + 1
    lastState <- state
  }
  count
}

go <- function(snapshots, nClusters = 10) {
  rules <- snapshots
  nRules <- ncol(rules)
  scores <- c(0)
  clusters <- sapply(1:nRules, function(i) list(i))
  redScore <- 0
  while (nRules > 1) {
    jaccMat <- makeJaccMat(rules)
    bestJacc <- max(jaccMat)
    bestIndex <- which(jaccMat == bestJacc)[[1]] - 1
    r <- bestIndex %% nRules + 1
    c <- bestIndex %/% nRules + 1
    
    newRule <- rules[,r] & rules[,c]
    rules <- rules[,c(-r, -c)]
    rules <- cbind(rules, newRule)
    nRules <- ncol(rules)
    
    score <- calcScore(rules)
    scores <- c(scores, score)
    
    if (nRules >= nClusters) {
      newCluster <- list(unlist(c(clusters[r], clusters[c])))
      clusters <- clusters[c(-r, -c)]
      clusters <- list.append(clusters, newCluster)
      redScore <-score
    }
    
    print(nRules)
    print(score / nrow(snapshots))
    # print(bestJacc)
    cat("\n")
  }
  plot(scores, col=(scores==redScore)+1)
  return (clusters)
}

clusterIndex <- function(clusters, id) {
  which(sapply(1:length(clusters), function(i) id %in% unlist(clusters[i])))
}

runMe <- function (nClusters) {
  snapshots <- as.matrix(read_csv(paste0(folder, "feature-states.csv"), col_names = FALSE))
  snapshots <- snapshots | snapshots
  features <- read_csv(paste0(folder, "features.csv"), col_names = TRUE)
  clusters <- go(snapshots, nClusters)
  features$cluster <- sapply(features$id, function(i) clusterIndex(clusters, i + 1))
  
  features <- features[order(features$cluster),]
  lastCluster <- 1
  for (i in 1:nrow(features)) {
    cluster <- features$cluster[i]
    if (cluster != lastCluster) cat("\n")
    lastCluster <- cluster
    cat(sprintf("%02d %02d: %s\n", features$cluster[i], features$id[i], features$name[i]))
  }
  
  write.csv(features, paste0(folder, "feature-clusters.csv"))
}
