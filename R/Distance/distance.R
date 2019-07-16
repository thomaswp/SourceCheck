library('plyr')

distance <- read.csv("../../data/csc200/all/analysis/distance.csv")

sapply(names(distance)[5:ncol(distance)], function(metric) cor(distance[,metric], distance$similarity, method="spearman"))

plot(distance$similarity, distance$SourceCheck)
