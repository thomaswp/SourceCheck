library(readr)
library(cluster)
library(tsne)
library(ggplot2)
library(rlist)
library(factoextra)

jaccMat <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-jaccard.csv", col_names = FALSE))
jaccMat <- 1 - jaccMat

domMat <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-dominate.csv", col_names = FALSE))

orderMat <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-order.csv", col_names = FALSE))
orderMat <- abs(orderMat)

# disMat <- (jaccMat + orderMat) / 2
disMat <- jaccMat

features <- read_csv("../../data/csc200/all/analysis/squiralHW/features.csv", col_names = TRUE)

fviz_nbclust(disMat, pam, "gap")

embed <- tsne(disMat, k=2, max_iter = 1000, epoch=500)
embed1d <- tsne(disMat, k=1, max_iter = 1000, epoch=500)

clusters <- pam(disMat, 7)

features$x <- embed[,1]
features$y <- embed[,2]
features$cluster <- as.factor(clusters$clustering)
ggplot(features, aes(x=x, y=y, color=cluster)) + geom_point(size=3)

features$z <- embed1d
ggplot(features, aes(x=z, y=0, color=cluster)) + geom_point(size=3)


snapshots <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-states.csv", col_names = FALSE))
snapshots <- snapshots | snapshots

jacc <- function(x, y) sum(x & y) / sum(x | y)
jacci <- function(i, j) jacc(rules[,i], rules[,j])

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

go <- function(nClusters = 10) {
  rules <- snapshots
  nRules <- ncol(rules)
  scores <- c(1)
  clusters <- sapply(1:nRules, function(i) list(i))
  while (nRules > nClusters) {
    jaccMat <- makeJaccMat(rules)
    bestJacc <- max(jaccMat)
    bestIndex <- which(jaccMat == bestJacc)[[1]] - 1
    r <- bestIndex %% nRules + 1
    c <- bestIndex / nRules + 1
    
    newRule <- rules[,r] & rules[,c]
    rules <- rules[,c(-r, -c)]
    rules <- cbind(rules, newRule)
    nRules <- ncol(rules)
    
    newCluster <- list(unlist(c(clusters[r], clusters[c])))
    clusters <- clusters[c(-r, -c)]
    clusters <- list.append(clusters, newCluster)
    
    score <- calcScore(rules)
    scores <- c(scores, score)
    
    print(nRules)
    print(score)
    print(bestJacc)
    cat("\n")
  }
  plot(scores)
  return (clusters)
}

clusterIndex <- function(clusters, id) {
  which(sapply(1:length(clusters), function(i) id %in% unlist(clusters[i])))
}

clusters <- go(12)
features$cluster <- sapply(features$id, function(i) clusterIndex(clusters, i))

features <- features[order(features$cluster),]
for (i in 1:nrow(features)) {
  cat(sprintf("%02d %02d: %s\n", features$cluster[i], features$id[i], features$name[i]))
}

