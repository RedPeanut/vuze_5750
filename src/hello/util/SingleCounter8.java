package hello.util;

public class SingleCounter8 {
	
	private static SingleCounter8 instance;
	private int count = 1;
	
	public static SingleCounter8 getInstance() {
		if (instance == null)
			instance = new SingleCounter8();
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
