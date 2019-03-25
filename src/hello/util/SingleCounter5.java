package hello.util;

public class SingleCounter5 {
	
	private static SingleCounter5 instance;
	private int count = 1;
	
	public static SingleCounter5 getInstance() {
		if (instance == null)
			instance = new SingleCounter5();
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
