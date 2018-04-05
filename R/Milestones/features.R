library(readr)
library(cluster)
library(tsne)
library(ggplot2)
library(factoextra)

disMat <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-jaccard.csv", col_names = FALSE))
disMat <- 1 - disMat

disMat <- as.matrix(read_csv("../../data/csc200/all/analysis/squiralHW/feature-order.csv", col_names = FALSE))
disMat <- abs(disMat)

features <- read_csv("../../data/csc200/all/analysis/squiralHW/features.csv", col_names = TRUE)

fviz_nbclust(disMat, pam, "gap")

embed <- tsne(disMat, k=2, max_iter = 5000, epoch=500)

clusters <- pam(disMat, 8)

features$x <- embed[,1]
features$y <- embed[,2]
features$cluster <- as.factor(clusters$clustering)

ggplot(features, aes(x=x, y=y, color=cluster)) + geom_point(size=3)

features <- features[order(features$cluster),]
for (i in 1:nrow(features)) {
  cat(features$cluster[i])
  cat(" ")
  cat(features$name[i])
  cat("\n")
}
