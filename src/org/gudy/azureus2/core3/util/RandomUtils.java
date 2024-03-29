/*
 * Created on Dec 30, 2005
 * Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 *
 */
package org.gudy.azureus2.core3.util;

import java.security.SecureRandom;
import java.util.Random;

import org.gudy.azureus2.core3.config.COConfigurationManager;

/**
 *	@author MjrTom
 *		2006/Jan/02:	added various methods, including some java.util.Random method aliases
 */

public class RandomUtils {
	
	public static final Random RANDOM = new Random(System.currentTimeMillis());
	public static final String INSTANCE_ID;

	static {
		byte[] bytes = new byte[3];
		RANDOM.nextBytes(bytes);
		INSTANCE_ID = Base32.encode(bytes).toLowerCase();
	}

	public static final SecureRandom SECURE_RANDOM = new SecureRandom();

	/**
	 * Generate a random array of bytes.
	 * @param num_to_generate number of bytes to generate
	 * @return random byte array
	 */
	public static byte[] generateRandomBytes(int num_to_generate) {
		byte[] id = new byte[ num_to_generate ];
		RANDOM.nextBytes(id);
		return id;
	}


	/**
	 * Generate a random string of charactors.
	 * @param num_to_generate number of chars to generate
	 * @return random char string
	 */
	public static String generateRandomAlphanumerics(int num_to_generate) {
		String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

		StringBuilder buff = new StringBuilder(num_to_generate);

		for (int i=0; i < num_to_generate; i++) {
			int pos = (int)(RANDOM.nextDouble() * alphabet.length());
			buff.append(alphabet.charAt( pos ));
		}

		return buff.toString();
	}


	public static final int LISTEN_PORT_MIN = 10000;
	public static final int LISTEN_PORT_MAX = 65535;


	/**
	 * Generate a random port number for binding a network IP listening socket to.
	 * NOTE: Will return a valid non-privileged port number >= LISTEN_PORT_MIN and <= LISTEN_PORT_MAX.
	 * @return random port number
	 */

	public static int generateRandomNetworkListenPort() {
		return (generateRandomNetworkListenPort(LISTEN_PORT_MIN, LISTEN_PORT_MAX));
	}

	public static int generateRandomNetworkListenPort(
		int		minPort,
		int		maxPort) {
		if (minPort > maxPort) {
			int temp 	= minPort;
			minPort	= maxPort;
			maxPort	= temp;
		}
		
		if (maxPort > LISTEN_PORT_MAX)
			maxPort = LISTEN_PORT_MAX;
		if (maxPort < 1)
			maxPort = 1;
		if (minPort < 1)
			minPort = 1;
		if (minPort > maxPort)
			minPort = maxPort;
		
		// DON'T use NetworkManager methods to get the ports here else startup can hang
		int	existingTcp	= COConfigurationManager.getIntParameter("TCP.Listen.Port");
		int existingUdp	= COConfigurationManager.getIntParameter("UDP.Listen.Port");
		int existingUdp2	= COConfigurationManager.getIntParameter("UDP.NonData.Listen.Port");
		int port = minPort;
		for (int i=0;i<100;i++) {
			int min 	= minPort;
			port 		= min + RANDOM.nextInt(maxPort + 1 - min);
			// skip magnet ports
			if (port >= 45100 && port <= 45110) {
				continue;
			}
			if (port != existingTcp && port != existingUdp && port != existingUdp2) {
				return port;
			}
		}
		return (port);
	}

	/**
	 * Generates a random +1 or -1
	 * @return +1 or -1
	 */
	public static int generateRandomPlusMinus1() {
		return RANDOM.nextBoolean() ? -1:1;
	}

	public static float nextFloat() {
		return RANDOM.nextFloat();
	}

	public static void nextBytes(byte[] bytes) {
		RANDOM.nextBytes(bytes);
	}

	public static void nextSecureBytes(byte[] bytes) {
		SECURE_RANDOM.nextBytes(bytes);
	}

	public static byte[] nextSecureHash() {
		byte[] hash = new byte[20];

		SECURE_RANDOM.nextBytes(hash);

		return (hash);
	}

	public static byte[] nextHash() {
		byte[] hash = new byte[20];

		RANDOM.nextBytes(hash);

		return (hash);
	}

	public static int nextInt(int n) {
		return RANDOM.nextInt(n);
	}

	public static byte nextByte() {
			return (byte)RANDOM.nextInt();
	}

	public static int nextInt() {
			return RANDOM.nextInt();
	}

	public static int nextAbsoluteInt() {
		return ((RANDOM.nextInt() << 1 ) >>> 1);
	}

	public static long nextLong() {
			return RANDOM.nextLong();
	}

	public static long nextLong(long n) {
		if (n > Integer.MAX_VALUE) {

			while (true) {

				long rand 	= nextAbsoluteLong();

				long res	= rand % n;

					// deal with non-uniformity as rand not generally divisible by n

				if (rand - res + (n-1) >= 0) {

					return (res);
				}
			}
		}

			return ((long)RANDOM.nextInt((int)n));
	}


	public static long nextAbsoluteLong() {
		return ((RANDOM.nextLong() << 1 ) >>> 1);
	}

	public static long nextSecureAbsoluteLong() {
		while (true) {
			long val = Math.abs( SECURE_RANDOM.nextLong());
			if (val >= 0) {
				return (val);
			}
		}
	}

	/**
	 * @return random int between 0 and max-1. e.g. param of 10 returns 0->9
	 */
	public static int generateRandomIntUpto(int max) {
		return nextInt(max);
	}

	/**
	 * @return random int between min and max, e.g params of [5,7] returns 5,6 or 7
	 */
	public static int generateRandomIntBetween(int min, int max) {
		return min +generateRandomIntUpto(max + 1 - min);
	}
}
