# SourceCheck

This repository contains code for the SourceCheck data-driven hint generation algorithm [[1](https://people.engr.ncsu.edu/twprice/website/files/EDM%202017.pdf)]. It also has a number of supporting projects for parsing Snap logs (and others), serving hints to a client, and evaluating their quality. The contained projects are as follows:

* **QualityScore** (submodule): Dependency, containing code for [evaluating the quality of data-driven hints](http://go.ncsu.edu/hint-quality-data).
* **SnapParser**: For parsing Snap! .xml files and log data from [iSnap](go.ncsu.edu/isnap).
* **JavaParser**: Code for parsing Java ASTs for use in hint generation.
* **CTD**: Code for SourceCheck and CTD hint generation algorithms.
  * Dependencies: `QualityScore`
* **iSnap**: Snap-specific hint generation code, used in the [iSnap](go.ncsu.edu/isnap) project. Temporarily also contains Python hint generation code on the `cs108` branch.
  * Dependencies: `SnapParser`, `CTD`
* **Hint Server**: Simple servlet for taking in a student's code, generating a hint, and serving it back as JSON. Currently works for Snap, Java (`blackbox` branch) and Python (`cs108` branch)
  * Dependencies: `iSnap`, `JavaParser` (in the `blackbox` branch only)
* **Templater**: Project for manually defining a template for generating hints, using expert rather than student data.
  * Dependencies: None
* **Hint Evaluation** (not for public use): Evaluation code intended only for NCSU use (but containing no private student data).
  * Dependencies: `Datasets` (see below), `Templater`

Private NCSU-only components (not on public github)
* **R**: Repository of R evaluation code from studies.
* **data**: Folder with relevant data (e.g. submitted records, etc.).
* **Datasets**: Java definitions of each study's data.

**Note**: Each of these sub-project has a README.md inside of it explaining it further.

## Setup

First, clone the repository [with submodules](https://stackoverflow.com/questions/3796927/how-to-git-clone-including-submodules), e.g.

```
git clone --recurse-submodules url
```

The projects here are designed for use with [Eclipse](http://www.eclipse.org/). For the HintSerer to work properly, you may need to select the "EE" version of Eclipse, or install the [Web Tools Platform](http://www.eclipse.org/webtools/). If you don't already have it, make sure to install the [m2e plugin](http://www.eclipse.org/m2e/) as well.

Once you have cloned this repo, open Eclipse and choose File->Import->Existing Projects into workspace and select the root folder of this repository. Select all projects and import them.

If the projects take a while to import the first time, it is because Eclipse is downloading dependencies. The projects in this repo use [maven](http://maven.apache.org/) to manage dependencies. It should integrate normally into your workflow, but it may cause you a bit of troubleshooting. If you prefer to use maven via command line, the projects will not compile due to 3rd party dependencies. The CTD and HintEvaluation projects contain .bat scripts to install these libraries locally, but currently their respective .pom files have the dependencies commented out to make configuration easier on Eclipse, without the need to install and configure maven.

### Using Data from iSnap

**Note**: These instructions are designed for NCSU use, but can be modified to work for other institutions.

To use this repository, you will need a dataset. These are not versioned for privacy reasons. When you have a dataset (in .csv format), place it in folder (e.g. data/csc200). This file contains output from a single table with all logs.

You then need to find or create a corresponding Dataset class in the [datasets](Datasets/src/edu/isnap/datasets/) package. See the existing Dataset classes for examples (e.g. `Fall2016`).

Next, open the [RunLogSplitter](Datasets/src/edu/isnap/datasets/run/RunLogSplitter.java) class, scroll to the bottom and edit the main method:

    public static void main(String[] args) throws IOException {
        // Replace "Fall2015" with the dataset you want to load
        splitStudentRecords(Fall2015.dataFile);
    }

Run this file to separate the single log file into more a more manageable set of logs files for individual assignment attempts. It will take a while, and you should only ever need to run it once. When it is finished, check the `data/csc200/{dataset}/parsed` folder. You should see folders for each assignment in your dataset, containing .csv files representing each assignment attempt in the dataset.

See more information on data folder in the [data/README.md](data/README.md) file.

## Contributing

We welcome contributions, e.g. adapting SourceCheck to work with a new language.

### Code Style

The code in this repository is in various states of cleanliness, but new code should endeavor to be as clean as possible. This repository uses standard Java code style, and it is recommended you enable some settings in Eclipse to maintain this:

* Preferences->General->Editors->Text Editors->Print margin columns: Set this value to 100 and for new code, try to keep lines under 100 characters in length.
* Preferences->Java->Editor->Save Actions: Check the "Perform the selected action on save" box, and uncheck "Format source code," but check "Organize Imports" and "Additional Actions." Click "Configure..." and check "Remove trailing whitespace." This will ensure your code does not have excess tabs/spaces and that it uses only needed imports.
