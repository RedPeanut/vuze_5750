package hello.util;

public class SingleCounter6 {
	
	private static SingleCounter6 instance;
	private int count = 1;
	
	public static SingleCounter6 getInstance() {
		if (instance == null)
			instance = new SingleCounter6();
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
