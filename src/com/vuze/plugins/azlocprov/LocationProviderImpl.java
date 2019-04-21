/*
 * Created on Mar 28, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.vuze.plugins.azlocprov;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.plugins.utils.LocationProvider;
import com.maxmind.geoip.Country;
import com.maxmind.geoip.LookupService;

import hello.util.Log;

public class LocationProviderImpl extends LocationProvider {

	private static String TAG = LocationProviderImpl.class.getSimpleName();
	
	private static final int[][] FLAG_SIZES = {{18,12},{25,15}};

	private final String	pluginVersion;
	private final File		pluginDir;

	private final boolean	hasImagesDir;

	private volatile boolean is_destroyed;

	private volatile LookupService	ls_ipv4;
	private volatile LookupService	ls_ipv6;

	private Set<String>	failed_dbs = new HashSet<String>();

	protected LocationProviderImpl(String _pluginVersion, File _pluginDir) {

		pluginVersion	= _pluginVersion==null?"":_pluginVersion;
		pluginDir 		= _pluginDir;

		hasImagesDir = new File(pluginDir, "images" ).isDirectory();
	}

	@Override
	public String getProviderName() {
		return ("Vuze Location Provider");
	}

	@Override
	public long getCapabilities() {
		return (CAP_COUNTY_BY_IP | CAP_FLAG_BY_IP | CAP_ISO3166_BY_IP);
	}
	
	private LookupService getLookupService(String databaseName) {
		if (failed_dbs.contains(databaseName)) {
			return (null);
		}
		if (is_destroyed) {
			return (null);
		}
		File dbFile = new File(pluginDir, databaseName);
		try {
			LookupService ls = new LookupService(dbFile);
			if (ls != null) {
				System.out.println("Loaded " + dbFile);
				return (ls);
			}
		} catch (Throwable e) {
			Debug.out("Failed to load LookupService DB from " + dbFile, e);
		}
		failed_dbs.add(databaseName);
		return (null);
	}

	private LookupService getLookupService(InetAddress ia) {
		LookupService result;
		if (ia instanceof Inet4Address) {
			result = ls_ipv4;
			if (result == null) {
				if (pluginVersion.length() > 0 && Constants.compareVersions(pluginVersion, "0.1.1") > 0) {
					result = ls_ipv4 = getLookupService("GeoIP_" + pluginVersion + ".dat");
				}
				if (result == null) {
					result = ls_ipv4 = getLookupService("GeoIP.dat");
				}
			}
		} else {
			result = ls_ipv6;
			if (result == null) {
				if (pluginVersion.length() > 0 && Constants.compareVersions(pluginVersion, "0.1.1") > 0) {
					result = ls_ipv6 = getLookupService("GeoIPv6_" + pluginVersion + ".dat");
				}
				if (result == null) {
					result = ls_ipv6 = getLookupService("GeoIPv6.dat");
				}
			}
		}
		return (result);
	}

	private Country getCountry(InetAddress ia) {
		if (ia == null) {
			return (null);
		}
		LookupService ls = getLookupService(ia);
		if (ls == null) {
			return (null);
		}
		Country result;
		try {
			result = ls.getCountry(ia);
		} catch (Throwable e) {
			result = null;
		}
		return (result);
	}

	public String getCountryNameForIP(InetAddress address, Locale in_locale) {
		Country country = getCountry(address);
		if (country == null) {
			return (null);
		}
		Locale country_locale = new Locale("", country.getCode());
		try {
			country_locale.getISO3Country();
			return (country_locale.getDisplayCountry(in_locale));
		} catch (Throwable e) {
			return (country.getName());
		}
	}

	public String getISO3166CodeForIP(InetAddress address) {
		Country country = getCountry(address);
		String result;
		if (country == null) {
			result = null;
		} else {
			result = country.getCode();
		}
		return (result);
	}

	public int[][] getCountryFlagSizes() {
		return (FLAG_SIZES);
	}
	
	public InputStream getCountryFlagForIP(InetAddress address, int size_index) {
		
		String code = getISO3166CodeForIP( address );
		if ( code == null ){
			return( null );
		}
		String flag_file_dir 	= (size_index==0?"18x12":"25x15");
		String flag_file_name 	= code.toLowerCase() + ".png";
		if ( hasImagesDir ){
			File ff = new File( pluginDir, "images" + File.separator + flag_file_dir + File.separator + flag_file_name );
			if ( ff.exists()){
				try{
					return( new ByteArrayInputStream( FileUtil.readFileAsByteArray( ff )));
				}catch( Throwable e ){
					Debug.out( "Failed to load " + ff, e );
				}
			}
		}
		return(getClass().getClassLoader().getResourceAsStream("com/vuze/plugins/azlocprov/images/" + flag_file_dir + "/" + flag_file_name ));
	}

	protected void destroy() {
		is_destroyed = true;
		if (ls_ipv4 != null) {
			ls_ipv4.close();
			ls_ipv4 = null;
		}
		if (ls_ipv6 != null) {
			ls_ipv6.close();
			ls_ipv6 = null;
		}
	}

	@Override
	public boolean isDestroyed() {
		return (is_destroyed);
	}

	public static void main(String[] args) {
		try {
			//File _pluginDir = new File("C:\\Projects\\development\\azlocprov")
			String _pluginVersion = "0.1.7.7";
			File _pluginDir = new File("/Users/jkkim/Library/Application Support/Azureus/plugins/azlocprov");

			LocationProviderImpl prov = new LocationProviderImpl(_pluginVersion, _pluginDir);

			//String myip = "1.244.159.36";
			InetAddress ia = InetAddress.getByAddress(new byte[] {(byte)1,(byte)244,(byte)159,(byte)36});
			Log.d(TAG, ""+prov.getCountry(ia).getName());
			Log.d(TAG, ""+prov.getLookupService(ia).getLocation(ia).longitude);
			Log.d(TAG, ""+prov.getLookupService(ia).getLocation(ia).latitude);
			
			ia = InetAddress.getByName("www.whatismyip.com");
			Log.d(TAG, prov.getCountry(ia).getName());
			Log.d(TAG, ""+prov.getLookupService(ia).getLocation(ia).longitude);
			Log.d(TAG, ""+prov.getLookupService(ia).getLocation(ia).latitude);
			
			ia = InetAddress.getByName("whatismyipaddress.com");
			Log.d(TAG, prov.getCountry(ia).getName());
			Log.d(TAG, ""+prov.getLookupService(ia).getLocation(ia).longitude);
			Log.d(TAG, ""+prov.getLookupService(ia).getLocation(ia).latitude);
			
			/*System.out.println(prov.getCountry(InetAddress.getByName("www.vuze.com")).getCode());
			System.out.println(prov.getCountry(InetAddress.getByName("2001:4860:4001:801::1011")).getCode());
			System.out.println(prov.getCountryNameForIP(InetAddress.getByName("bbc.co.uk"), Locale.FRANCE));
			System.out.println(prov.getCountryFlagForIP(InetAddress.getByName("bbc.co.uk"), 0));
			System.out.println(prov.getCountryFlagForIP(InetAddress.getByName("bbc.co.uk"), 1));*/
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
