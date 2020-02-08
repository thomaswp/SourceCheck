
# HintServer

The HintServer is a Java servlet designed to serve hints to iSnap. To use the HintServer, you must generate hints using the [SnapHintBuilder](../iSnap/src/edu/isnap/hint/SnapHintBuilder.java)'s main method via running the [RunHintBuilder](../Datasets/src/edu/isnap/datasets/run/RunHintBuilder.java). This will generate HintBuilders for each assignment, cache them, and copy the cached files to the HintServer's WEB-INF folder. If using Eclipse, you'll need to refresh the HintServer folder before starting the server, so that it recognizes the updated data files.

Before starting the server, you also need to change the properties for HintServer project:
- Select the 'Deployment Assembly', Add CTD, Datasets, iSnap, and SnapParser;
- Select Project Facets, Check 'Dynamic Web Module', 'Java', and 'JavaScript'.

If any error occurs, usually that is due to the Java Compiler version which is not set properly. You may need to change the Java Compiler to the newest version for all the projects. You can do it by clicking 'Markers', selecting problems, right click and choose quick fix. After resolving all the issues, you can start the server. A default message 'Loaded cache for guess1Lab' shows on the web page if the server runs as expected.