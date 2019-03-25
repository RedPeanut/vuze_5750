package hello.util;

public class SingleCounter7 {
	
	private static SingleCounter7 instance;
	private int count = 1;
	
	public static SingleCounter7 getInstance() {
		if (instance == null)
			instance = new SingleCounter7();
		return instance;
	}
	
	public int getAndIncreaseCount() {
		return count++;
	}
	
	public int getCount() {
		return count;
	}
	
	public void increaseCount() {
		count++;
	}
	
}
