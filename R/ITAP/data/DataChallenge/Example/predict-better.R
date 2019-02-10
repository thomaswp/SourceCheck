if (!require(plyr)) install.packages("plyr")
library(plyr)
library(reshape2)
library(corrplot)
library(MASS)

###
# This is a simple example of how to build and evaluate a classifier for the Data Challenge.
# This example does not use any of the data in the MainEvents table, nor does it use students'
# source code to build a more expressive model. The primary purpose is to demonstrate:
# 1) How to extract appropriate attributes from a student's history for a given prediction and
# 2) How to use the provided 10-fold crossvalidation datasets to evaluate a classifier.
###

runMe <- function() {
  # Get all data
  predict <- read.csv("../Predict.csv")
  problems <- sort(unique(predict$ProblemID))
  
  events <- read.csv("../MainTable.csv")
  events$ServerTimestamp <- strptime(events$ServerTimestamp, format="%Y-%m-%dT%H:%M:%S")
  
  progress <- read.csv("Progress.csv")
  progress <- progress[progress$ProblemID %in% predict$ProblemID,]
  
  allAttrs <- addAttributes(predict, getProblemStats(predict))
  #simpleModel <- lm(FirstCorrect ~ pCorrectForProblem + priorPercentCorrect, data=allAttrs)
  #allAttrs$pred <- predict(simpleModel, allAttrs)
  #allAttrs$pred <- pmin(pmax(allAttrs$pred, 0), 1)
  #allAttrs$perf <- allAttrs$FirstCorrect - allAttrs$pred
  allAttrs$perf <- allAttrs$FirstProgress
  allAttrs$perf <- allAttrs$FirstCorrect
  #hist(allAttrs$perf)
  pred <- sapply(problems, function(prob1) sapply(problems, function(prob2) {
    if (prob1 == prob2) return (NA)
    p1 <- allAttrs[allAttrs$ProblemID==prob1,]
    p2 <- allAttrs[allAttrs$ProblemID==prob2,]
    p1$p1Order <- p1$StartOrder
    p2$p2Order <- p2$StartOrder
    p1$p1Perf <- p1$perf
    p2$p2Perf <- p2$FirstCorrect
    shared <- merge(p1[,c("SubjectID", "p1Order", "p1Perf")], p2[,c("SubjectID", "p2Order", "p2Perf")], all=F)
    #print(shared$p1Order)
    shared <- shared[shared$p1Order <= shared$p2Order,]
    if (nrow(shared) < 5) return (NA)
    suppressWarnings(test <- cor.test(shared$p1Perf + 0, shared$p2Perf + 0))
    if (is.na(test$p.value) || test$p.value >= 0.1) return (NA)
    #if (prob2 == "isEvenPositiveInt" && prob1 == "howManyEggCartons") {
    #  print(shared)
    #  print(test)
    #}
    return (test$estimate)
  }))
  rownames(pred) <- colnames(pred) <- problems
  corrplot(pred)
  sum(pred > 0.3, na.rm=T)
  
  # Build a model using full dataset for training
  model <- buildModel(allAttrs)
  summary(model)
  
  {
    # Crossvalidate the model
    results <- crossValidate()
    evaluateByProblem <- evaluatePredictions(results, c("ProblemID"))
    evaluateOverall <- evaluatePredictions(results, c())
  }
  
  # Write the results
  write.csv(results, "cv_predict.csv", row.names = F)
  write.csv(evaluateByProblem, "evaluation_by_problem.csv", row.names = F)
  write.csv(evaluateOverall, "evaluation_overall.csv", row.names = F)
}

getProblemStats <- function(data) {
  # Calculate the average success rate on each problem and merge it into the data
  problemStats <- ddply(data, c("ProblemID"), summarize, 
                        pCorrectForProblem=mean(FirstCorrect), medAttemptsForProblem=median(Attempts))
  return (problemStats)
}


# Calculate some additional attributes to use in prediction
addAttributes <- function(data, problemStats) {
  data <- merge(data, problemStats)
  
  data <- merge(data, events[,c("Order", "ServerTimestamp")], by.x="StartOrder", by.y = "Order", all.x=T)
  data <- data[order(data$SubjectID, data$StartOrder),]
  #print(as.numeric(data$ServerTimestamp))
  data$time <- as.numeric(data$ServerTimestamp)
  data$time <- data$time - min(data$time)

  data <- merge(data, progress, all.x=T)
  data <- data[order(data$SubjectID, data$StartOrder),]
  data$FirstProgress[is.na(data$FirstProgress) | data$FirstCorrect] <- 1
  data$BestProgress[is.na(data$BestProgress) | data$EverCorrect] <- 1
  
  # Now we want to calculate the *prior* rate of success/completion for each
  # student before they attempted each problem
  
  # First, order the data by subject and then by chronological order
  data <- data[order(data$SubjectID, data$StartOrder), ]
  
  # Now we declare the columns
  # If we have no other data (e.g. first problem), we default to a 50% success rate
  data$priorPercentCorrect <- 0.5
  # Same with the percent of problems ever completed correctly
  data$priorPercentCompleted <- 0.5
  data$priorAttempts <- 0
  data$pStudentCorrect <- 0
  data$elapsed <- 0
  
  probs <- list()
  for (problem in problems) {
    data[,problem] <- 0
  }
  
  lastStudent <- ""
  # Go through each row in the data...
  for (i in 1:nrow(data)) {
    # If this is a new student, reset our counters
    student <- data$SubjectID[i]
    if (student != lastStudent) {
      attempts <- 0
      firstCorrectAttempts <- 0
      completedAttempts <- 0
      lastTime <- data$time[i]
      for (problem in problems) {
        probs[problem] <- 0
      }
    }
    lastStudent <- student
    
    data$elapsed[i] <- data$time[i] - lastTime
    lastTime <- data$time[i]
    
    for (problem in problems) {
      data[i,problem] <- probs[problem]
    }
    probs[data$ProblemID[i]] <- data$FirstCorrect[i] * 2 - 1
    
    data$priorAttempts[i] <- attempts
    # If this isn't their first attempt, calculate their prior percent correct and completed
    if (attempts > 0) {
      # When calculating attributes to use in prediction, make sure
      # to only use information that occurred *before* the event you
      # are predicting. In this case, we only calculate the prior percent
      # correct/completed, and don't include any information from this row
      data$priorPercentCorrect[i] <- firstCorrectAttempts / attempts
      data$priorPercentCompleted[i] <- completedAttempts / attempts
    }
    
    otherCorrect <- data[data$SubjectID == student & data$ProblemID != data[i,]$ProblemID,]$FirstCorrect
    data$pStudentCorrect[i] <- if (length(otherCorrect) == 0) 0 else mean(otherCorrect)
    
    
    # Now update the number of problems they attempted and got right (on their first try)
    attempts <- attempts + 1
    #if (data$FirstCorrect[i]) {
    if (data$FirstProgress[i] >= 0.8) {
      firstCorrectAttempts <- firstCorrectAttempts + 1
    }
    if (data$EverCorrect[i]) {
      completedAttempts <- completedAttempts + 1
    }
  }
  
  return (data)
}

oversample <- function(data, size) {
  data[sample(1:nrow(data), size, replace=T),]
}

# Build a simple logistic model with the given training data
buildModel <- function(training) {
  set.seed(12345)
  
  nCorrect <- sum(training$FirstCorrect)
  nIncorrect <- sum(!training$FirstCorrect)
  if (nCorrect > nIncorrect) {
    oversample <- rbind(training[training$FirstCorrect,],
                        oversample(training[!training$FirstCorrect,], size = nrow(training) / 2))  
  } else {
    oversample <- rbind(training[!training$FirstCorrect,],
                        oversample(training[training$FirstCorrect,], size = nrow(training) / 2))
  }
  # oversample <- training
  
  #print(mean(oversample[,"nearestBusStop"]))
  probs <- problems[problems %in% colnames(oversample)]
  oversample <- oversample[,c("FirstCorrect", "priorPercentCorrect", as.character(probs))]
  model <- lm(FirstCorrect ~ ., data=oversample)
  
  relevantProblems <- names(model$coefficients)[!is.na(model$coefficients)]
  relevantProblems <- relevantProblems[relevantProblems %in% probs]
  oversample <- oversample[,c("FirstCorrect", "priorPercentCorrect", as.character(relevantProblems))]
  model <- lm(FirstCorrect ~ ., data=oversample)#, family = "binomial")
  simple <- lm(FirstCorrect ~ 1, data=oversample)
  
  #model <- stepAIC(simple, scope=list("upper"=model, "lower"=simple), method="forward", trace=F)
  
  return (model)
}

# Build a model with the training data and make predictions for the test data
makePredictions <- function(training, test) {
  
  # Add attributes to the test dataset, but use the per-problem performance statistics from the test dataset
  # (since we would not actually know these for a real test dataset)
  problemStats <- getProblemStats(training)
  
  training <- addAttributes(training, problemStats)
  test <- addAttributes(test, problemStats)
  test$estimate <- 0
  
  for (problem in unique(test$ProblemID)) {
    print(paste("  Problem:", problem))
    testProbl <- test[test$ProblemID == problem,]
    row <- pred[problem,]
    removeCols <- names(row)[is.na(row) | row <= 0]
    # print(removeCols)
    model <- buildModel(training[training$ProblemID == problem, !(colnames(training) %in% removeCols)])
    if (problem == "leftoverCandy") {
      print(summary(model))
    }
    suppressWarnings(test$estimate[test$ProblemID == problem] <- predict(model, testProbl))
  }
  test$prediction <- test$estimate > 0.5
  return (test)
}

# Load each training/test data split and build a model to evaluate
crossValidate <- function() {
  results <- NULL
  for (fold in 0:9) {
    print(paste("Fold:", fold))
    training <- read.csv(paste0("../CV/Fold", fold, "/Training.csv"))
    test <- read.csv(paste0("../CV/Fold", fold, "/Test.csv"))
    test <- makePredictions(training, test)
    test$fold <- fold
    results <- rbind(results, test)
  }
  results
}

# Evaluate a given set of classifier prediction results using a variety of metrics
evaluatePredictions <- function(results, groupingCols) {
  eval <- ddply(results, groupingCols, summarize,
                pCorrect = mean(FirstCorrect),
                pPredicted = mean(prediction),
                tp = mean(FirstCorrect & prediction),
                tn = mean(!FirstCorrect & !prediction),
                fp = mean(!FirstCorrect & prediction),
                fn = mean(FirstCorrect & !prediction),
                accuracy = tp + tn,
                precision = tp / (tp + fp),
                recall = tp / (tp + fn)
  )
  eval$f1 <- 2 * eval$precision * eval$recall / (eval$precision + eval$recall)
  pe <- eval$pCorrect * eval$pPredicted + (1-eval$pCorrect) * (1-eval$pPredicted)
  eval$kappa <- (eval$accuracy - pe) / (1 - pe)
  return (eval)
}
