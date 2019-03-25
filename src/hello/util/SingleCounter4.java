package hello.util;

public class SingleCounter4 {
	
	private static SingleCounter4 instance;
	private int count = 1;
	
	public static SingleCounter4 getInstance() {
		if (instance == null)
			instance = new SingleCounter4();
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
