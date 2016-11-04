
library(tsne)
library(vegan)
library(ggplot2)

rm(list=ls())

matrix <- read.csv("../../data/csc200/fall2016/analysis/guess2HW/matrix-submitted.csv")
matrix <- read.csv("../../data/csc200/fall2016/analysis/guess2HW/matrix-path.csv")
x <- data.matrix(matrix)

states <- read.csv("../../data/csc200/fall2016/analysis/guess2HW/snapshots-submitted.csv", stringsAsFactors = F)
states <- read.csv("../../data/csc200/fall2016/analysis/guess2HW/snapshots-path.csv", stringsAsFactors = F)
states$g <- substr(states$attempt, 1, 1)
states <- states[order(states$id),]

mds <- metaMDS(x)
states$x <- mds$points[,1]
states$y <- mds$points[,2]

ggplot(states, aes(x, y)) + geom_point(aes(shape=hints>2, color=g, size=submitted)) + geom_path(aes(color=g, group=attempt), alpha=0.3)
ggplot(states[(states$attempt %in% states[states$hints>2,]$attempt),], aes(x, y)) + geom_point(aes(shape=hints>2, color=g, size=submitted)) + geom_path(aes(color=g, group=attempt), alpha=0.3)
ggplot(states, aes(x, y)) + geom_point(aes(color=hints > 2))


t <- tsne(x)
states$x <- t[,1]
states$y <- t[,2]

states <- states[states$attempt != "fe8e6eac-5c8b-491b-84c0-e9b165d64510",]
