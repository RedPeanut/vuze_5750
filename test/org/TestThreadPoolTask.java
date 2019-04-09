package org;

import org.gudy.azureus2.core3.util.ThreadPoolTask;

public class TestThreadPoolTask {
	
	public static void main(String[] args) {
		
		new ThreadPoolTask() {
			@Override public void interruptTask() {}
			@Override public void runSupport() {}
		};
		
	}
	
}
