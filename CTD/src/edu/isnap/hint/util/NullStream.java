package edu.isnap.hint.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class NullStream extends PrintStream {

	public final static NullStream instance = new NullStream();

	public NullStream() {
		super(new OutputStream() {
			@Override
			public void write(int b) throws IOException { }
		});
	}
}
