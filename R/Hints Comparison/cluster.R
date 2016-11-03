
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
states$g <- substr(states$attempt, 1, 2)

ggplot(states, aes(x, y)) + geom_point(aes(shape=submitted, color=attempt)) + geom_line(aes(group=attempt), alpha=0.3) + scale_color_discrete()
