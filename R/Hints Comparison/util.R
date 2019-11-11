library(ggplot2)
library(plyr)
library(reshape2)

safeMean <- function(x) {
  mean(x, na.rm = T)
}

ifNA <- function(x, backup) {
  ifelse(is.na(x), backup, x)
}

se <- function(x) sqrt(var(x, na.rm=T)/sum(!is.na(x)))
first <- function(x) {
  if (length(x) == 0) return (NA)
  return (x[1])
}

cohen.d <- function(xs, ys) {
  xs <- xs[!is.na(xs)]
  ys <- ys[!is.na(ys)]
  m1 <- mean(xs)
  m2 <- mean(ys)
  sd1 <- sd(xs)
  sd2 <- sd(ys)
  n1 <- length(xs)
  n2 <- length(ys)
  
  s <- sqrt(((n1 - 1) * sd1 * sd1 + (n2 - 1) * sd2 * sd2) / (n1 + n2 - 2))
  
  return ((m1 - m2) / s)
  
}

condCompare <- function(x, cond, test = wilcox.test, filter = T) {
  x <- x[filter]
  cond <- cond[filter]
  compareStats(x[cond], x[!cond], test)
}

compareStats <- function(x, y, test = wilcox.test) {
  #x <- x[!is.na(x)]
  #y <- y[!is.na(y)]
  print(sprintf("Nx = %d (%d NA); Ny = %d (%d NA)", sum(!is.na(x)), sum(is.na(x)), sum(!is.na(y)), sum(is.na(y))))
  print(paste("Mx =", mean(x, na.rm=T), "SD =", sd(x, na.rm=T)))
  print(paste("My =", mean(y, na.rm=T), "SD =", sd(y, na.rm=T)))
  print(paste("Medx =", median(x, na.rm=T), "IQR =", IQR(x, na.rm=T)))
  print(paste("Medy =", median(y, na.rm=T), "IQR =", IQR(y, na.rm=T)))
  # print(paste("Medx =", median(x), "IQR =", median(x) - IQR(x)/2, "-", median(x) + IQR(x)/2))
  # print(paste("Medy =", median(y), "IQR =", median(y) - IQR(y)/2, "-", median(y) + IQR(y)/2))
  print(paste("Cohen's D=", cohen.d(x,y)))
  t <- test(x, y)
  if (identical(test, wilcox.test)) print(wilcoxLatex(t))
  t
}

c.merge <- function(x, y) {
  cols <- intersect(names(x), names(y))
  rbind(x[,cols], y[,cols])
}

spearmanLatex <- function(x, y) {
  test <- cor.test(x, y, method="spearman")
  sprintf("($\rho=%.03f$; $S=%.0f$; $p=%.03f$)",
          test$estimate, test$statistic, test$p.value)
}

wilcoxLatex <- function(test) {
  sprintf("($W=%.1f$; $p=%.03f$)",
          test$statistic, test$p.value)
}