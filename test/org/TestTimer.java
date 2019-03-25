package org;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.ThreadPool;
import org.junit.Test;

import junit.framework.TestCase;

public class TestTimer extends TestCase {
	
	//@Test
	public void testTimer() {
		ThreadPool threadPool = new ThreadPool("ThreadPool:test", 1);
		threadPool.run(new AERunnable() {
			@Override
			public void runSupport() {
				System.out.println("runSuport() is called...");
			}
		});
		
		while (true) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
