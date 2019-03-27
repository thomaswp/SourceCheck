library(readr)
library(cluster)
library(tsne)
library(factoextra)
library(MASS)
library(reshape2)

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
  
  # Make sure no 0s
  for (i in 1:nrow(disMat)) for (j in 1:nrow(disMat)) if (i != j && disMat[i,j] == 0) disMat[i,j] <- 0.001
  
  
  #Plot just 3 from each
  ns <- 3
  range <- c(1:(ns*25), 1:(ns*25)+10*25, 1:(ns*25)+20*25)
  #range <- 1:30 * 25
  subMat <- disMat[range, range]
  #subFit <- cmdscale(subMat,eig=TRUE, k=2)
  #subFit <- isoMDS(subMat, k=2)
  subFit <- isoMDS(log(subMat+1), k=2)
  subSamples <- samples[range,]
  subSamples$x <- subFit$points[,1]
  subSamples$y <- subFit$points[,2]
  plotTraj(subSamples)
  
  # Plot distance matrix
  ggplot(data = melt(subMat), aes(x=Var1, y=Var2, fill=value)) + 
    geom_tile()
  
  #embed <- tsne(disMat, k=2, max_iter = 200, epoch=100)
  
  # plot all
  #fit <- cmdscale(disMat,eig=TRUE, k=2)
  fit <- isoMDS(log(disMat+1), k=2)
  samples$x <- fit$points[,1]
  samples$y <- fit$points[,2]
  plotTraj(samples)
  
  # plot just submitted solutions
  ggplot(samples[samples$i == 25,], aes(x=x, y=y, color=semester)) + geom_point()
}


plotTraj <- function(data) {
  ggplot(data, aes(x=x, y=y, color=semester, group=student)) + coord_fixed(ratio = 1) +
    geom_point(aes(size = i)) +
    geom_path()
}