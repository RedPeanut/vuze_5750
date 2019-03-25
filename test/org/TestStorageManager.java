package org;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.Test;

public class TestStorageManager {
	
	@Test
	public void importContact() {
		DataInputStream	dis = null;
		try {
			File dataDir = new File("/Users/jkkim/Library/Application Support/Azureus/dht");
			File target = new File(dataDir, "contacts.dat");
			dis = new DataInputStream(new FileInputStream(target));
			
			int	num = dis.readInt();
			System.out.println("num = " + num);
			for (int i=0;i<num;i++) {
				long timeAlive = dis.readLong();
				//System.out.println("timeAlive = " + timeAlive);
				
				byte ct = dis.readByte();
				byte version = dis.readByte();
				
				//System.out.println("ct = " + ct);
				//System.out.println("version = " + version);
				
				int len = dis.readByte()&0xff;
				//System.out.println("len = " + len);
				
				byte[] data	= new byte[len];
				dis.read(data);
				
				int	port = dis.readShort()&0xffff;
				
				InetSocketAddress addr = new InetSocketAddress(InetAddress.getByAddress(data), port);
				System.out.println(String.format("[%d] addr = %s", i, addr));
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (dis != null ) try { dis.close(); } catch (Exception e) {};
		}
		
	}
}
