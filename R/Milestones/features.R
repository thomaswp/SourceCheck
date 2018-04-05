library(readr)
library(cluster)
library(tsne)
library(ggplot2)
library(factoextra)
library(Matrix)

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

features <- features[order(features$cluster),]
for (i in 1:nrow(features)) {
  cat(features$cluster[i])
  cat(" ")
  cat(features$name[i])
  cat("\n")
}

jacc <- function(x, y) sum(x & y) / sum(x | y)
jacci <- function(i, j) jacc(snapshots[,i], snapshots[,j])

states <- read_csv("../../data/csc200/all/analysis/squiralHW/feature-states.csv", col_names = FALSE)
test <- princomp(states)
snapshots <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-states.csv", col_names = FALSE))
snapshots <- snapshots | snapshots

jaccMat <- matrix(nrow=30, ncol=30)
for (i in 1:30) for (j in 1:30) jaccMat[i,j] = jacci(i, j)
disMat <- 1 - jaccMat

