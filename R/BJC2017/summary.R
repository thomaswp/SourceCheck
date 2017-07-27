library(plyr)

summary <- read_csv("data/summary.csv", 
  col_types = cols(
    classroom = col_factor(levels = c("Palooza_1", "Palooza_2", "Palooza_3", "Palooza_4")), 
    minutes = col_integer(), start = col_character()))
summary$start <- strftime(summary$start)

projects <- ddply(summary, .(assignmentID, classroom, hints, userID), summarise,
  mi=head(which(minutes == max(minutes)), 1),
  projectID=projectID[mi], minutes=minutes[mi], start=start[mi])
