package edu.isnap.ctd.hint;

import edu.isnap.ctd.graph.Node;

public interface Hint {
	String from();
	String to();
	String data();
	Node outcome();
}
