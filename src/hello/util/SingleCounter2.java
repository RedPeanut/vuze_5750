package hello.util;

public class SingleCounter2 {
	
	private static SingleCounter2 instance;
	private int count = 1;
	
	public static SingleCounter2 getInstance() {
		if (instance == null)
			instance = new SingleCounter2();
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
