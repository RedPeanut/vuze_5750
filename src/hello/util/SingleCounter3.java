package hello.util;

public class SingleCounter3 {
	
	private static SingleCounter3 instance;
	private int count = 1;
	
	public static SingleCounter3 getInstance() {
		if (instance == null)
			instance = new SingleCounter3();
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
