library(readr)
library(cluster)
library(tsne)
library(factoextra)
library(MASS)

compressDisMat <- function(disMat) {
  ns <- 25
  for (i in 1:(floor(nrow(disMat) / ns))-1) {
    totalDis <- disMat[i * ns + 1, i * ns + ns]
    #print(paste("== ", i * ns + 1, i * ns + ns, totalDis))
    #totalDis <- 3
    for (j in 1:ns) {
      row <- i * ns + j
      for (k in 1:ns) {
        col <- i * ns + k
        dis <- totalDis / (ns-1) * abs(j-k)
        disMat[row,col] <- dis
        #print(paste(row,col,dis))
      }
      #print(paste(i, j, row))
    }
  }
  disMat
}

oldAnalysis <- function() {
  
  originalDisMat <- as.matrix(read_csv(paste0("data/ted-poly.csv"), col_names = FALSE))
  samples <-data.frame("semester"=c(rep("S17", 250), rep("F17", 250), rep("S19", 250)), "student"=floor(0:749/25), "i"=rep(1:25,250*3))
  samples$student <- as.factor(samples$student)
  
  disMat <- originalDisMat
  # disMat <- compressDisMat(disMat)
  
  #smallDisMat <- disMat[sample(nrow(samples), 250, F), sample(nrow(samples), 250, F)]
  #sil <- sapply(2:50, function(i) pam(disMat, i, diss=T)$silinfo$avg.width)
  #plot(unlist(sil))
  
  # 38 expert-authored states
  # clusters <- pam(disMat, 38, diss=T)
  # samples$cluster <- as.factor(clusters$clustering)
  # samples$name <- paste0("X", samples$id + 1)
  # samples$medoid <- samples$name %in% clusters$medoids
  # write.csv(samples, paste0(folder, "samples-clustered.csv"))
  
  for (i in 1:nrow(disMat)) for (j in 1:nrow(disMat)) if (i != j && disMat[i,j] == 0) disMat[i,j] <- 0.001
  
  
  ss <- 10
  range <- 1:(ss*25) + 25 * 15
  subMat <- disMat[range, range]
  #subFit <- cmdscale(subMat,eig=TRUE, k=2)
  #subFit <- isoMDS(subMat, k=2)
  subFit <- isoMDS(log(subMat+1), k=2)
  subSamples <- samples[range,]
  subSamples$x <- subFit$points[,1]
  subSamples$y <- subFit$points[,2]
  plotTraj(subSamples)
  
  #embed <- tsne(disMat, k=2, max_iter = 200, epoch=100)
  
  fit <- cmdscale(disMat,eig=TRUE, k=2)
  fit <- isoMDS(log(disMat+1), k=2)
  samples$x <- fit$points[,1]
  samples$y <- fit$points[,2]
  # plot all
  plotTraj(samples)
  # plot a subsample
  plotTraj(samples[samples$student %in% sample(1:30, 10),])
  
  # plot just first points - why do students not all start in the same place?
  ggplot(samples[samples$i == 1,], aes(x=x, y=y, color=semester)) + geom_point()
}


plotTraj <- function(data) {
  ggplot(data, aes(x=x, y=y, color=student, group=student)) + coord_fixed(ratio = 1) +
    geom_point(aes(size = i)) +
    geom_path()
}