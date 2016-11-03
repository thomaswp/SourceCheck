
library(tsne)
library(vegan)
library(ggplot2)

rm(list=ls())


matrix <- read.csv("../../data/csc200/fall2016/analysis/guess1Lab/matrix-path.csv")
x <- data.matrix(matrix)
t <- tsne(x)

states <- read.csv("../../data/csc200/fall2016/analysis/guess1Lab/snapshots-path.csv", stringsAsFactors = F)
states <- states[order(states$id),]

states$x <- t[,1]
states$y <- t[,2]
states$g <- substr(states$attempt, 1, 1)

ggplot(states, aes(x, y)) + geom_point(aes(shape=submitted, color=g)) + geom_line(aes(color=g, group=attempt), alpha=0.3) + scale_color_discrete()


mds <- metaMDS(x)
states$x <- mds$points[,1]
states$y <- mds$points[,2]
