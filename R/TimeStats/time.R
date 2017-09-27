
library(readr)
library(reshape2)
library(plyr)
library(ggplot2)

spring2016 <- read_csv("~/GitHub/SnapHints/data/csc200/spring2016/analysis/attempts.csv")
fall2016 <- read_csv("~/GitHub/SnapHints/data/csc200/fall2016/analysis/attempts.csv")
spring2017 <- read_csv("~/GitHub/SnapHints/data/csc200/spring2017/analysis/attempts.csv")

all <- rbind(spring2016, fall2016[,1:15], spring2017)