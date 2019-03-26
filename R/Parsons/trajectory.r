library(readr)
library(cluster)
library(tsne)
library(factoextra)


oldAnalysis <- function() {
  
  disMat <- as.matrix(read_csv(paste0("data/ted-poly.csv"), col_names = FALSE))
  samples <-data.frame("semester"=c(rep("S17", 250), rep("F17", 250), rep("S19", 250)), "student"=floor(0:749/25), "i"=rep(1:25,250*3))
  samples$student <- as.factor(samples$student)
  
  smallDisMat <- disMat[sample(nrow(samples), 250, F), sample(nrow(samples), 250, F)]
  sil <- sapply(2:50, function(i) pam(disMat, i, diss=T)$silinfo$avg.width)
  plot(unlist(sil))
  
  # 38 expert-authored states
  # clusters <- pam(disMat, 38, diss=T)
  # samples$cluster <- as.factor(clusters$clustering)
  # samples$name <- paste0("X", samples$id + 1)
  # samples$medoid <- samples$name %in% clusters$medoids
  # write.csv(samples, paste0(folder, "samples-clustered.csv"))
  
  embed <- tsne(disMat, k=2, max_iter = 200, epoch=100)
  samples$x <- embed[,1]
  samples$y <- embed[,2]
  # plot all
  ggplot(samples, aes(x=x, y=y, color=semester)) + geom_path(aes(group="student"), arrow=arrow(length=unit(0.25, "cm"), type="closed"))
  # plot a subsample
  ggplot(samples[samples$student %in% sample(1:30, 10),], aes(x=x, y=y, color=semester)) + geom_path(aes(group="student"), arrow=arrow(length=unit(0.25, "cm"), type="closed"))
  
  # plot just first points - why do students not all start in the same place?
  ggplot(samples[samples$i == 1,], aes(x=x, y=y, color=semester)) + geom_point()
}