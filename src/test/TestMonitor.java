package test;

import org.gudy.azureus2.core3.util.AEMonitor;

public class TestMonitor {
	
	public static void main(String[] args) {
		
		final AEMonitor mon = new AEMonitor("Test monitor");
		
		Thread a = new Thread(new Runnable() {
			@Override
			public void run() {
				
				try {
					mon.enter("A");
					Thread.sleep(3000);
					System.out.println("[A] run() is called...");
					//a.start();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					mon.exit();
				}
				
			}
		});
		a.start();
		
		Thread b = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					mon.enter("B");
					Thread.sleep(2000);
					System.out.println("[B] run() is called...");
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					mon.exit();
				}
			}
		});
		b.start();
		
		Thread c = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					mon.enter("C");
					Thread.sleep(1000);
					System.out.println("[C] run() is called...");
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					mon.exit();
				}
			}
		});
		c.start();
		
		System.out.println("[outer] main() is called...");
		/*while (true) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}*/

	}
	
	
	
}
