package test;

import org.gudy.azureus2.core3.util.Timer;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;

public class TestTimer {
	
	public static void main(String[] args) {
		
		Timer timer = new Timer("Test timer");
		timer.addEvent(1000, new TimerEventPerformer() {
			@Override
			public void perform(TimerEvent event) {
				System.out.println(">>>>> 1");
				System.out.println("perform() is called...");
				System.out.println("event = " + event);
			}
		});
		timer.addPeriodicEvent(2000, new TimerEventPerformer() {
			@Override
			public void perform(TimerEvent event) {
				System.out.println(">>>>> 2");
				System.out.println("perform() is called...");
				System.out.println("event = " + event);
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

	/*public static void main(String[] args) {
		
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
	}*/
	
	/*
	public static void main(String[] args) {
		
		TimerEventPeriodic event = SimpleTimer.addPeriodicEvent(
				"TestTimer:test",
				5000,
				new TimerEventPerformer() {
					public void perform(TimerEvent ev) {
						System.out.println("perform() is called...");
						Throwable t = new Throwable();
						t.printStackTrace();
					}
				}
			);
		
		while (true) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}
	//*/
}