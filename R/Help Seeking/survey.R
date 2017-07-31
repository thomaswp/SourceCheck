library(readr)

loadData <- function() {
  presurvey <- read_csv("data/presurvey_numbers.csv", 
    col_types = cols(Q6 = col_character(), 
      RecordedDate = col_datetime(format = "%m/%d/%Y %H:%M")))
  tutors <- read_csv("data/tutors.csv")
  users <- read_csv("data/users.csv", 
    col_types = cols(
      autoHelpAssignment = col_factor(levels = c("brickWall", "guess1Lab")),
      created = col_character(),
      tutoredAssignment = col_factor(levels = c("brickWall", "guess1Lab"))))
  attempts <- read_csv("../../data/help-seeking/fall2016-spring2017/analysis/attempts.csv",
    col_types = cols(assignment = col_factor(levels = c("brickWall", "guess1Lab"))))
  hints <- read_csv("../../data/help-seeking/fall2016-spring2017/analysis/hints.csv", 
    col_types = cols(assignment = col_factor(levels = c("brickWall", "guess1Lab"))))
  
}
