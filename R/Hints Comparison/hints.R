library(ggplot2)
library(plyr)
library(reshape2)

csv <- function(dir, file) {
  read.csv(paste(dir, file, sep=""))
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

loadData <- function() {
  rm(list=ls())
  
  dir2015 <- "../../data/csc200/fall2015/analysis/"
  dir2016 <- "../../data/csc200/fall2016/analysis/"
  
  projs2015 <- csv(dir2015, "attempts.csv")
  projs2016 <- csv(dir2016, "attempts.csv")
  
  projs <<- rbind(projs2015, projs2016)
  projs$follow2 <- projs$followed >= 2;
  projs$hint3 <- projs$hints >= 3;
  
  totals <<- ddply(projs[,-3], c("dataset", "assignment"), colwise(mean))
  gradesByHints <- ddply(projs[,-3], c("dataset", "assignment", "hint3"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), n=length(grade))
  gradesByFollowed <- ddply(projs[,-3], c("dataset", "assignment", "follow2"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), n=length(grade))
  # projs <<- projs[projs$hints < 60,]
  # totalsNO <<- ddply(projs[,-1], c(), colwise(sum))
  projs$pFollowed <<- ifelse(projs$hints == 0, 0, projs$followed / projs$hints)
  
  hints <<- csv(dir2016, "hints.csv") 
  hints <<- hints[hints$id %in% projs$id,]
}

testGrades <- function(assignment) {
  assignment <- "squiralHW" # TODO, remove
  g15 <- projs[projs$dataset == "Fall2015" & projs$assignment == assignment,]
  g16 <- projs[projs$dataset == "Fall2016" & projs$assignment == assignment,]
  g16H <- g16[g16$hint3,]
  g16F <- g16[g16$follow2,]
  
  # Significant for guess2HW
  compareStats(g15$grade, g16$grade)
  # Not significant  
  compareStats(g16$grade, g16H$grade)
  # Significant for squiralHW
  compareStats(g16$grade, g16F$grade)
  # Not significant
  compareStats(g15$grade, g16F$grade)
}

compareStats <- function(x, y, test = t.test) {
  print(paste("Mx =", mean(x), "SD =", sd(x)))
  print(paste("My =", mean(y), "SD =", sd(y)))
  print(paste("Medx =", median(x), "IQR =", IQR(x)))
  print(paste("Medy =", median(y), "IQR =", IQR(y)))
  # print(paste("Medx =", median(x), "IQR =", median(x) - IQR(x)/2, "-", median(x) + IQR(x)/2))
  # print(paste("Medy =", median(y), "IQR =", median(y) - IQR(y)/2, "-", median(y) + IQR(y)/2))
  print(paste("Cohen's D=", cohen.d(x,y)))
  test(x, y)
}

plotFollowedGrades <- function() {
  counts <- ddply(projs2016, c("dataset", "followed", "grade"), summarize, n=length(hints))
  counts <- counts[!is.na(counts$grade),]
  ggplot(counts, aes(followed, grade)) + geom_point(aes(size=sqrt(n)))
}

plotHintsGrades <- function() {
  counts <- ddply(projs2016, c("dataset", "hints", "grade"), summarize, n=length(hints))
  counts <- counts[!is.na(counts$grade),]
  ggplot(counts, aes(hints, grade)) + geom_point(aes(size=sqrt(n)))
}

findHintObjs <- function() {
  hints$nextObj <- sapply(1:nrow(hints), function(i) {
    row <- hints[i,]
    working <- objs[objs$id==as.character(row$id) & objs$timePerc > row$timePerc, "obj"]
    if (length(working) == 0) return ("End")
    as.character(working[[1]])
  })
  hints$nextObj <- as.factor(hints$nextObj)
  
  hints$lastObj <- sapply(1:nrow(hints), function(i) {
    row <- hints[i,]
    done <- objs[objs$id==as.character(row$id) & objs$timePerc < row$timePerc, "obj"]
    if (length(done) == 0) return ("Start")
    as.character(tail(done, 1))
  })
  hints$lastObj <- as.factor(hints$lastObj)
  
  hints
}

binGrade <- function(grade) {
  projs <- as.ordered(c("F", "CD", "B", "A"))
  if (grade == 1) return (projs[4])
  else if (grade >= 0.85) return (projs[3])
  else if (grade >= 0.65) return (projs[2])
  return (projs[1])
}

plotStudent <- function(id) {
  data <- snapshot[snapshot$id==id,]
  ggplot(data, aes(x = time, y = distance)) +
    geom_line() +
    geom_smooth() + 
    geom_point(aes(color=type))
}

plotStudents <- function(bins = 40) {
  snapshot$bin <- floor(snapshot$timeNorm * bins)
  data <- ddply(snapshot, "bin", summarize, meanDis=mean(distance), seDis=se(distance), hints = sum(type=="hint"), followed = sum(!is.na(isTaken)))
  #return (data)
  ggplot(data, aes(x = bin, y = meanDis)) +
    geom_line() +
    geom_ribbon(aes(ymin = meanDis-seDis, ymax = meanDis+seDis), alpha=0.3) +
    geom_point(aes(size=hints), color="red") 
    #geom_point(aes(size=followed), color="blue")
}

plotRequestedGrades <- function() {
  hintQ <- ddply(projs, c("grade", "hints"), "nrow")
  qplot(hints, grade, data=hintQ, size=nrow) +
    labs(x="Hints hints", y="Grade", size="Frequency", title="Grade vs Hints hints")
}

plotFollowedGrades <- function() {
  hintQ <- ddply(projs, c("grade", "followed"), "nrow")
  qplot(followed, grade, data=hintQ, size=nrow) +
    labs(x="Hints Followed", y="Grade", size="Frequency", title="Grade vs Hints Followed")
}

plotOverTime <- function(bins = 10) {
  hints <- hints[!hints$unchanged,]
  hints$bin <- floor(hints$editPerc * bins) + 1
  data <- ddply(hints, c("id", "bin"), summarize, accepted = sum(followed), rejected = sum(!followed), total=length(followed))
  for (id in unique(data$id)) {
    for (bin in 1:bins) {
      if (sum(data$id == id & data$bin == bin) == 0) {
        data <- rbind(data, data.frame(id=id, bin=bin, accepted=0, rejected=0, total=0))
      } 
    }
  }
  orderedIds <- projs$id[order(projs$pFollowed)]
  data$id <- match(data$id, orderedIds)
  data$id <- paste(data$id, orderedIds[data$id])
  data <- melt(data, id=c("id", "bin"))
  data$group <- paste(data$id, data$variable)
  ggplot(data, aes(x=bin, y=value, color=variable, group=group)) +
    geom_line() +
    geom_point() +
    facet_wrap(~id)
}

nf <- function(x, n) {
  if (length(x) <= n) return (-1)
  return (x[[n]])
}

nthCor <- function(nth) {
  nth <<- nth
  hints <- hints[!hints$unchanged,]
  data <- ddply(hints, c("id"), summarize,
               perc = mean(followed), 
               nFollowed = sum(followed),
               percAfter = mean(followed[-1:-nth]),
               nFollowedAfter = sum(followed[-1:-nth]),
               nthFollowed = nf(followed, nth))
  data <- data[data$nthFollowed >= 0,]
  data
  #print (cor(data$perc, data$nthFollowed))
  #plot(data$perc ~ jitter(data$nthFollowed))
}

se <- function(x) sqrt(var(x, na.rm=TRUE)/sum(!is.na(x)))

plotAfter <- function(cutoff = 4) {
  data <- NA
  i <- 1
  while (T) {
    nc <- nthCor(i)
    if (nrow(nc) < cutoff) break
    row <- ddply(nc, "nthFollowed", summarize, mean=mean(nFollowed), se=se(nFollowed))
    row$n <- i
    data <- rbind(data, row)
    i <- i + 1
  }
  data <- data[-1,]
  
  data$nthFollowed <- ordered(data$nthFollowed)
  
  ggplot(data, aes(x=n, y=mean, color=nthFollowed, group=nthFollowed)) +
    geom_line() + geom_point() +
    geom_ribbon(aes(ymin=mean-se, ymax=mean+se), alpha=0.3)
}

plotCor <- function(cutoff = 4) {
  data <- NA
  i <- 1
  while (T) {
    nc <- nthCor(i)
    if (nrow(nc) < cutoff) break
    test <- cor.test(nc$nFollowedAfter, nc$nthFollowed)
    row <- data.frame(n=i, cor=test$estimate, min=test$conf.int[[1]], max=test$conf.int[[2]], p=test$p.value)
    data <- rbind(data, row)
    i <- i + 1
  }
  data <- data[-1,]
  data <- data[data$p < 0.05,]
  
  ggplot(data, aes(x=n, y=cor)) +
    geom_line() + geom_point() +
    geom_ribbon(aes(ymin=min, ymax=max), alpha=0.3)
}

plotObjs <- function() {
  data1 <- ddply(objs, "obj", summarize, mean=mean(timePerc), se=se(timePerc))
  data1$type <- "time"
  data2 <- ddply(objs, "obj", summarize, mean=mean(duration), se=se(duration))
  data2$type <- "duration"
  data <- rbind(data1, data2)
  ggplot(data, aes(x = obj, y = mean, fill=type)) +
    geom_bar(stat="identity", position="dodge") +
    geom_errorbar(position="dodge", aes(ymin=mean-se, ymax=mean+se))
}

hintsTests <- function() {
  # 42.8% of hints were followed
  mean(hints$followed)
  # but 58.3% of objective completing hints were followed  
  mean(hints[hints$obj != "",]$followed)

  # Hint requests were mostly normal, with a bit of bimodality  
  hist(hints$timePerc)
  
  # followed hints generally came a bit earlier
  ggplot(hints, aes(x=as.factor(followed), y=timePerc)) + geom_boxplot()
  # this difference is significant
  wilcox.test(hints$timePerc ~ hints$followed)
  # but the difference in means was not much (6% or so)
  
  hints$early <- hints$editPerc < 0.5
  evl <- ddply(hints, c("id"), summarize, 
               percEarly = sum(followed & early) / sum(early), 
               rejEarly = sum(early & !followed), 
               perc = mean(followed), 
               er = sum(early), lt = sum(!early), n = length(early),
               firstFollowed = followed[[1]])
  cor.test(evl$percEarly, evl$lt)
  cor.test(evl$perc, evl$lt)
  cor.test(evl$perc, evl$n)
  
  plot(evl$perc ~ jitter(evl$firstFollowed))
  wilcox.test(evl$perc ~ evl$firstFollowed)
  cor.test(evl$perc, evl$firstFollowed)
  
  cor.test(evl$rejEarly, evl$late)
  
  # Maybe the more hints asked for early, the more asked for late (non-sig)
  plot(evl$early, evl$late)
  cor.test(evl$early, evl$late)
  
  table(hints$followed, hints$delete)
  table(hints$followed, hints$change < -5)
}

subgoalTests <- function() {
  
  # Of those who check goals, their accuracy correlated with grade
  cor.test(part$percSat, part$grade)
  plot(jitter(part$grade) ~ part$percSat, col=as.factor(part$used))
  # But their total correct completed goals did not
  cor.test(part$satisfied, part$grade)
  plot(jitter(part$grade) ~ part$satisfied, col=as.factor(part$used))
  # But... that could just be that the people who wait until the end do better?
  
  # Strong correlation between number of goals you said you got and how many were right
  # But that kind of meaningless b/c one bounds the other
  cor.test(part$finished, part$satisfied)
  plot(jitter(part$finished) ~ part$satisfied, col=as.factor(part$used))
  
  # Still percentage was generally high  
  hist(part$percSat)
  mean(part$percSat)
  boxplot(part$percSat)
  
  # All with low median gap finished "all objectives"  
  plot(part$finished ~ log(part$gap), col=as.factor(part$used))
  # But they were not correct
  plot(part$percSat ~ log(part$gap), col=as.factor(part$used))
  plot(part$satisfied ~ log(part$gap), col=as.factor(part$used))
  # No difference in accuracy
  wilcox.test(part$percSat ~ part$used)
  wilcox.test(part$satisfied ~ part$used)
  
  median(part[part$used,]$percSat)
  median(part[!part$used,]$percSat)
  
  # Those using the subgoals perform about the same as the others
  wilcox.test(goals$grade ~ goals$used)
  plot(jitter(goals$grade) ~ log(goals$gap + 1), col=as.factor(goals$used))
  
  all <- merge(projs, goals)
  # No correlation at all between hint and subgoal usage
  cor.test(all$finished, all$hints)
}

projTests <- function() {
  
  # all non-significantly positively correlate to performance
  cor.test(projs$hints, projs$grade)
  cor.test(projs$followed, projs$grade)
  cor.test(projs$pFollowed, projs$grade) # none are significant
  
  # Hint usage and percFollowed are very correlated
  cor.test(projs$pFollowed, projs$hints)
  
  # students following 1+ hints don't do significantly better
  wilcox.test(projs[projs$followed > 1, "grade"], projs[projs$followed <= 1, "grade"])
  
  # No students following 1+ hints misses more than 1 objective
  table(projs[projs$followed > 1, "grade"])
  
  # students following 1+ hints do 4% better... but this isn't really a meaningful measure with nonnormal data
  mean(projs[projs$followed > 1, "grade"]) - mean(projs[projs$followed <= 1, "grade"])
}