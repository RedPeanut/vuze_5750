package org;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.ThreadPool;

public class TestThreadPool {
	
	/*public static void main(String[] args) {
		
		System.out.println("start...");
		
		ThreadPool nonBlocking = new ThreadPool("Test thread pool", 5, true);
		
		for (int i = 0; i < 10; i++) {
			final int f_i = i;
			AERunnable r = new AERunnable() {
				@Override
				public void runSupport() {
					System.out.println(String.format("#%d is run...", f_i));
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
			nonBlocking.run(r);
		}
		
		System.out.println("end...");
		
		while (true) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}*/
	
	public static void main(String[] args) {
		
		System.out.println("start...");
		
		ThreadPool blocking = new ThreadPool("", 5);
		
		for (int i = 0; i < 10; i++) {
			final int f_i = i;
			AERunnable r = new AERunnable() {
				@Override
				public void runSupport() {
					System.out.println(String.format("#%d is run...", f_i));
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
			blocking.run(r);
		}
		System.out.println("end...");
	}
}
