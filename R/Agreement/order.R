library(readr)
library(plyr)
library(igraph)

data <- read_csv("data/fall2016-guess1Lab.csv")

nProjs <- length(unique(data$project))
labels <- ddply(data, "label", summarize, count=length(index), medIndex=median(index))
labels$perc = labels$count / nProjs

labels <- labels[labels$perc >= 0.3, ]

filtered <- data[data$label %in% labels$label,]

n <- nrow(labels)
mat <- matrix(nrow=n, ncol=n)
dimnames(mat) = list(labels$label, labels$label)

minPerc <- 0.85

edges <- vector("list", n * n)
edgeIndex <- 1
weights <- vector("list", n * n / 2)

for (i in 1:n) {
  for (j in i:n) {
    labelA <- labels$label[[i]]
    labelB <- labels$label[[j]]
    fa <- filtered[filtered$label == labelA,]
    fa$indexA <- fa$index
    fb <- filtered[filtered$label == labelB,]
    fb$indexB <- fb$index
    merged <- merge(fa[,c("project", "indexA")], fb[,c("project", "indexB")], by="project", all=F)
    if (nrow(merged) == 0) next
    aFirst <- mean(merged$indexA < merged$indexB)
    bFirst <- mean(merged$indexA > merged$indexB)
    if (nrow(merged) < n * 0.1) next
    if (aFirst > bFirst) {
      mat[i,j] = aFirst
      if (aFirst >= minPerc) {
        edges[[edgeIndex * 2 - 1]] <- labelA
        edges[[edgeIndex * 2]] <- labelB
        weights[[edgeIndex]] <- aFirst
        edgeIndex <- edgeIndex + 1
      }
    } else {
      mat[i,j] = -bFirst
      if (bFirst  >= minPerc) {
        edges[[edgeIndex * 2 - 1]] <- labelB
        edges[[edgeIndex * 2]] <- labelA
        weights[[edgeIndex]] <- bFirst
        edgeIndex <- edgeIndex + 1
      }
    }
  }
  if (i %% 10 == 0) print(i)
}

g <- graph(edges = as.character(Filter(Negate(is.null), edges)))
E(g)$weight <- as.numeric(Filter(Negate(is.null), weights))
V(g)$count <- sapply(V(g)$name, function(name) labels[labels$label==name,]$count)

# Warning, this loop takes a few minutes to complete, so only use it when you're sure
edgeEnds <- ends(g, E(g))
delete <- sapply(1:length(E(g)), function(i) {
  if (i %% 100 == 0) print(i)
  paths <- all_simple_paths(g, edgeEnds[i,1], edgeEnds[i,2], "out")
  return (length(paths) > 1)
})
deleteIdx <- (1:length(delete))[delete]
pruned <- delete.edges(g, deleteIdx)

write_graph(pruned, "C:/Users/Thomas/Desktop/fall2016-guess1Lab.graphml", "graphml")
