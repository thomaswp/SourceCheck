

read.qualtrics <- function(file) {
  data <- read.csv(file)
  data <- data[-1:-2,]
  for (i in 1:ncol(data)) {
    col <- data[,i]
    num <- suppressWarnings(as.numeric(as.character(col)))
    if (sum(!is.na(num)) > 0 && sum(!is.na(num)) / sum(col != "", na.rm=T) > 0.5) {
      data[,i] <- num
    } else if (class(col) == "factor") {
      data[,i] <- factor(col)
    }
  }
  data
}

loadData <- function() {
  consent <- read.qualtrics("data/consent.csv")
  post1 <- read.qualtrics("data/post1.csv")
  post2 <- read.qualtrics("data/post2.csv")
  preHelp <- read.qualtrics("data/pre-help.csv")
  postHelp <- read.qualtrics("data/post-help.csv")
  
  post2 <- post2[post2$assignmentID == "drawTriangles",]
  length(users <- Reduce(intersect, list(
    consent$userID, post1$userID, post2$userID, preHelp$userID, postHelp$userID
  )))
  
  users <- users[!(users %in% post1$userID[duplicated(post1$userID)])]
  
  post1 <- post1[post1$userID %in% users,]
  post2 <- post2[post2$userID %in% users,]
  preHelp <- preHelp[preHelp$userID %in% users,]
  postHelp <- postHelp[postHelp$userID %in% users,]
  
  table(preHelp$assignmentID)

}

