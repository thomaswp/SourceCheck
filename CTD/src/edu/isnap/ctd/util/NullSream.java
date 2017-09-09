package edu.isnap.ctd.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class NullSream extends PrintStream {

	public final static NullSream instance = new NullSream();

	public NullSream() {
		super(new OutputStream() {
			@Override
			public void write(int b) throws IOException { }
		});
	}
}
