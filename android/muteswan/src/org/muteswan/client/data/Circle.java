/*
Copyright 2011-2012 James Unger,  Chris Churnick.
This file is part of Muteswan.

Muteswan is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Muteswan is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Muteswan.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.muteswan.client.data;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.muteswan.client.Base64;
import org.muteswan.client.Crypto;
import org.muteswan.client.MuteLog;
import org.muteswan.client.MuteswanHttp;
import org.muteswan.client.Main;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;
import android.preference.PreferenceManager;

public class Circle {
     
	Circle.OpenHelper openHelper;
	
	final private String key;
	
	
	
		
	final private String shortname;
	final private String server;
	final private String notes = null;
	final private String keyHash;
	private MuteswanHttp muteswanHttp;

	
	public Context context;

	private Integer curLastMsgId = 0;

	private String postPolicy;
	private String authKey;
	private byte[] image;
	private String longDescription;
	private String description;
	private String[] keylist;
	
	private HashMap<Integer,MuteswanMessage> msgCache = new HashMap<Integer,MuteswanMessage>();

	public class OpenHelper extends SQLiteOpenHelper {

		public static final int DATABASE_VERSION = 12;
		public String databaseName = "muteswandb";
		public static final String MESSAGESTABLE = "messages";
		public static final String SIGTABLE = "signatures";
		public static final String LASTMESSAGES = "lastmessages";
		public static final String MANIFEST = "manifest";

		
	     
	      public OpenHelper(Context context, String circleHash) {
	    	  super(context, circleHash, null, DATABASE_VERSION);
	    	  databaseName = circleHash;
		}

		@Override
	      public void onCreate(SQLiteDatabase db) {
	         db.execSQL("CREATE TABLE " + MESSAGESTABLE + " (id INTEGER PRIMARY KEY, ringHash TEXT, msgId INTEGER, date DATE, message TEXT)");
	         db.execSQL("CREATE TABLE " + SIGTABLE + " (id INTEGER PRIMARY KEY, msgId INTEGER, ringHash TEXT, signature TEXT)");
	         db.execSQL("CREATE TABLE " + LASTMESSAGES + " (ringHash TEXT PRIMARY KEY, lastMessage INTEGER, lastCheck DATE)");
	         //db.execSQL("CREATE TABLE " + MANIFESTS + " (ringHash TEXT PRIMARY KEY, description TEXT, longdescription TEXT, image BLOB, authkey TEXT, postpolicy TEXT)");
	         db.execSQL("CREATE TABLE " + MANIFEST + " (key TEXT, value TEXT)");

		}

	      @Override
	      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	         db.execSQL("DROP TABLE IF EXISTS " + MESSAGESTABLE);
	         db.execSQL("DROP TABLE IF EXISTS " + SIGTABLE);
	         db.execSQL("DROP TABLE IF EXISTS " + LASTMESSAGES);
	         db.execSQL("DROP TABLE IF EXISTS " + MANIFEST);
	         onCreate(db);
	      }
	      
	      public void deleteData(SQLiteDatabase db) {
	    	  db.execSQL("DELETE FROM " + MESSAGESTABLE);
		      db.execSQL("DELETE FROM " + SIGTABLE);
		      db.execSQL("DELETE FROM " + LASTMESSAGES);
		      db.execSQL("DELETE FROM " + MANIFEST);
	      }
	      
	   }


  

	public Circle(Context context, String contents) {
		Integer plusIndx = contents.indexOf("+");
		Integer atIndx = contents.indexOf("@");
		
		if (plusIndx == -1 || atIndx == -1) {
			this.key = null;
		    this.shortname = null;
		    this.server = null;
		    this.keyHash = null;
			return;
		}
		
		String name = contents.substring(0,plusIndx);
		String key = contents.substring(plusIndx+1,atIndx);
		String srv = contents.substring(atIndx+1,contents.length());
		
		if (name == null || key == null || srv == null) {
			this.key = null;
	        this.shortname = null;
	        this.server = null;
	        this.keyHash = null;
			return;
		}
		
		
		
		this.key = key;
		this.shortname = name;
		this.server = srv;
		this.keyHash = Main.genHexHash(key);
		this.context = context;
		
	    curLastMsgId = 0;
		
	}
	
	public Circle(Context context, String key, String shortname, String server, MuteswanHttp muteswanHttp) {
		this.key = key;
		this.shortname = shortname;
		this.server = server;
		this.context = context;

		this.keyHash = Main.genHexHash(key);
		//SharedPreferences defPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
       	//Boolean aggroHttp = defPrefs.getBoolean("aggressivehttp", false);

		//if (aggroHttp) {
	  	this.muteswanHttp = muteswanHttp;
		//} else {
	    //	  this.muteswanHttp = muteswanHttp;
		//}
	}
	
	public Circle(Context context, String key, String shortname, String server) {
		this.key = key;
		this.shortname = shortname;
		this.server = server;
		this.context = context;
		this.keyHash = Main.genHexHash(key);

		
	    curLastMsgId = 0;
	}
	
	
	
	public void setCurLastMsgId(int lastMsg) {
		curLastMsgId = lastMsg;
	}
	
	
	
	public Circle.OpenHelper getOpenHelper() {
		if (this.openHelper == null)
			this.openHelper = new Circle.OpenHelper(context, Main.genHexHash(getFullText()));
		return(this.openHelper);
	}
	
	
	
	@SuppressWarnings("unused")
	private void initManifest() {
		String circleHash = Main.genHexHash(getFullText());
		SQLiteDatabase db = this.getOpenHelper().getWritableDatabase();
		
	
		
		Cursor cursor = db.query(Circle.OpenHelper.MANIFEST, new String[] { "value" }, "key = ?", new String[] { "description" }, null, null, null );
		if (cursor.moveToFirst() && cursor.getString(0) != null) {
			setDescription(cursor.getString(0));
		}
		cursor.close();
		
		cursor = db.query(Circle.OpenHelper.MANIFEST, new String[] { "value" }, "key = ?", new String[] { "longdescription" }, null, null, null );
		if (cursor.moveToFirst() && cursor.getString(0) != null) {
			setLongDescription(cursor.getString(0));
		}
		cursor.close();
		
		cursor = db.query(Circle.OpenHelper.MANIFEST, new String[] { "value" }, "key = ?", new String[] { "image" }, null, null, null );
		if (cursor.moveToFirst() && cursor.getBlob(0) != null) {
			setImage(cursor.getBlob(0));
		}
		cursor.close();
		
		cursor = db.query(Circle.OpenHelper.MANIFEST, new String[] { "value" }, "key = ?", new String[] { "postpolicy" }, null, null, null );
		if (cursor.moveToFirst() && cursor.getString(0) != null) {
			setPostPolicy(cursor.getString(0));
		}
		cursor.close();
		
		cursor = db.query(Circle.OpenHelper.MANIFEST, new String[] { "value" }, "key = ?", new String[] { "authkey" }, null, null, null );
		if (cursor.moveToFirst() && cursor.getString(0) != null) {
			setAuthKey(cursor.getString(0));
		}
		cursor.close();
		
		cursor = db.query(Circle.OpenHelper.MANIFEST, new String[] { "value" }, "key = ?", new String[] { "keylist" }, null, null, null );
		
		if (cursor.getCount() != 0) {
		  String[] keylist = new String[cursor.getCount()];
		  int count = 0;
		  while (cursor.moveToNext()) {
			keylist[count] = cursor.getString(0);
			count++;
		  }
		
		  if (count != 0)
			setKeylist(keylist);
		
		  if (cursor.moveToFirst() && cursor.getString(0) != null) {
			setAuthKey(cursor.getString(0));
		  }
		  cursor.close();
		
		
		  db.close();
		} else {
			cursor.close();
		}
		
		db.close();

		
	}
	
	public String getPostPolicy() {
		return(postPolicy);
	}

	public String getAuthKey() {
		return(authKey);
	}

	public byte[] getImage() {
		return(image);
	}
	
	public String[] getKeylist() {
		return(keylist);
	}

	public String getLongDescription() {
		return(longDescription);
	}

	public String getDescription() {
		return(description);
	}

	final public String getFullText() {
		return(getShortname()+"+"+getKey()+"@"+getServer());
	}
	
	private void setKeylist(String[] keylist) {
		this.keylist = keylist;
	}
	
	private void setPostPolicy(String policy) {
		this.postPolicy = policy;
	}

	private void setAuthKey(String key) {
		this.authKey = key;
	}

	private void setImage(byte[] image) {
		this.image = image;
	}

	private void setLongDescription(String description) {
		this.longDescription = description;
	}

	private void setDescription(String description) {
		this.description = description;
	}

	
	
	
	
	public String getKey() {
		return key;
	}

	
	
	public void initCache() {
		String circleHash = Main.genHexHash(getFullText());
		SQLiteDatabase db = this.getOpenHelper().getReadableDatabase();
		Cursor cursor = db.query(OpenHelper.MESSAGESTABLE, new String[] { "msgId","date", "message" }, "ringHash = ?", new String[] { circleHash }, null, null, "id desc limit 100" );
		while (cursor.moveToNext()) {
			String msgId = cursor.getString(0);
			String date = cursor.getString(1);
			String msgData = cursor.getString(2);
			
			msgCache.put(Integer.parseInt(msgId),new MuteswanMessage(this,Integer.parseInt(msgId),date,msgData));
		}
		
		cursor.close();
		db.close();
	}
	
	
	/**
	 * Gets the last known message from the database. Does not check tor.
	 * @return integer
	 */
	public Integer getLastMsgId(boolean closedb) {
		
		String circleHash = Main.genHexHash(getFullText());
		SQLiteDatabase db = this.getOpenHelper().getReadableDatabase();

		Integer lastMessageId = null;
		
		

		Cursor cursor = db.query(Circle.OpenHelper.LASTMESSAGES, new String[] { "lastMessage" }, "ringHash = ?", new String[] { circleHash }, null, null, "lastMessage desc" );
		if (cursor.moveToFirst()) {
			lastMessageId = cursor.getInt(0);
		}
		cursor.close();
	
		if (closedb)
		  db.close();
				

		curLastMsgId = lastMessageId;
		return(lastMessageId);
	}
	
	public Integer getLastCurMsgId(boolean closedb) {
		if (curLastMsgId == null || curLastMsgId == 0)
			curLastMsgId = getLastMsgId(closedb);
		return(curLastMsgId);
	}
	
	
	/*public MuteswanMessage getMsg(String id) throws ClientProtocolException, IOException {
		MuteswanMessage msg = null;
		msg = getMsgFromDb(id,true);
		if (msg == null) {
			
			msg = getMsgFromTor(Integer.parseInt(id));
			
			if (msg == null)
				return null;
			
			if (context != null) {
				if (msg.signatures[0] != null) {
				   saveMsgToDb(Integer.parseInt(id),msg.getDate(),msg.getMsg(),msg.signatures);
				} else {
				   saveMsgToDb(Integer.parseInt(id),msg.getDate(),msg.getMsg());
				}
			}
		}
		
		return(msg);
		
	}*/
	
	
	
	public MuteswanMessage getMsgFromDb(String id, Boolean closedb) {
		MuteswanMessage msg = null;
		
		if (msgCache.containsKey(Integer.parseInt(id))) {
			MuteLog.Log("Circle", "Fetched from msgCache: " + id);
			return (MuteswanMessage) (msgCache.get(Integer.parseInt(id)));
		}
		
		if (context == null)
			return(null);
		
		
		String circleHash = Main.genHexHash(this.getFullText());
		
		SQLiteDatabase db = getOpenHelper().getReadableDatabase();
		
		msg = getMsgFromDb(db,circleHash,id);
		
		if (closedb)
		 db.close();
		
		return(msg);
		
	}
	
	public MuteswanMessage getMsgFromDbWithIdentities(SQLiteDatabase db, String circleHash, String id) {
		MuteswanMessage msg = null;

		Cursor cursor = db.query(OpenHelper.MESSAGESTABLE, new String[] { "date", "message" }, "msgId = ? and ringHash = ?", new String[] { id, circleHash }, null, null, "id desc" );
		if (cursor.moveToFirst()) {
			String date = cursor.getString(0);
			String msgData = cursor.getString(1);
			
			Cursor cursorSig = db.query(OpenHelper.SIGTABLE, new String[] { "signature" }, "msgId = ? and ringHash = ?", new String[] { id, circleHash }, null, null, "signature desc" );
			
			//FIXME: max signatures?
			String[] signatures = new String[50];
			int i=0;
			SIG: while (cursorSig != null && cursorSig.moveToNext()) {
				String sigTxt = cursorSig.getString(0);
				signatures[i] = sigTxt;
				i++;
				if (cursor.isLast()) 
					break SIG;
			}
			cursorSig.close();
			
			if (signatures[0] != null) {
			   msg = new MuteswanMessage(this,Integer.parseInt(id),date,msgData,signatures);
			} else {
			   msg = new MuteswanMessage(this,Integer.parseInt(id),date,msgData);
			}
			cursor.close();
			//db.close();
			return(msg);
		}
		
		return(null);
		
	}
		
	public MuteswanMessage getMsgFromDb(SQLiteDatabase db, String circleHash, String id) {
		MuteswanMessage msg = null;
		
		if (Thread.currentThread().isInterrupted())
			return(null);

		Cursor cursor = db.query(OpenHelper.MESSAGESTABLE, new String[] { "date", "message" }, "msgId = ? and ringHash = ?", new String[] { id, circleHash }, null, null, "id desc" );
		if (cursor.moveToFirst()) {
			String date = cursor.getString(0);
			String msgData = cursor.getString(1);
			
			msg = new MuteswanMessage(this,Integer.parseInt(id),date,msgData);

			cursor.close();
			//db.close();
			return(msg);
		}
		
		cursor.close();
		return(null);
		
	}
	
	public MuteswanMessage getMsgFromTor(int id) throws ClientProtocolException, IOException, InterruptedIOException {
		HttpGet httpGet = new HttpGet("http://" + server + "/" + keyHash + "/" + id);
		MuteLog.Log("Circle", "Fetching message " + id);
    	HttpResponse resp = muteswanHttp.httpClient.execute(httpGet);
    	MuteLog.Log("Circle", "Fetched message " + id);
    	
    	return(parseMsgFromTor(id,resp));
    	
	}
	
	public ArrayList<MuteswanMessage> getMsgRangeFromTor(int max, int min) throws ClientProtocolException, IOException, InterruptedIOException {

		ArrayList<MuteswanMessage> msgs = new ArrayList<MuteswanMessage>();
		
		HttpGet httpGet = new HttpGet("http://" + server + "/" + keyHash + "/" + max + "-" + min);
		MuteLog.Log("Circle", "Fetching messages " + max + " to " + min);
    	HttpResponse resp = muteswanHttp.httpClient.execute(httpGet);
		MuteLog.Log("Circle", "Fetched messages " + max + " to " + min);

		
		String jsonString = EntityUtils.toString(resp.getEntity());
		if (jsonString == null) {
			MuteLog.Log("Circle", "getMsgRangeFromTor(): jsonString is null");
			return null;
		}
		
		try {
			JSONArray jsonArray = new JSONArray(jsonString);
			for (int i = 0; i<jsonArray.length();i++) {
				JSONObject jsonObj = jsonArray.getJSONObject(i);
				JSONObject contentObj = jsonObj.getJSONObject("content");
				String date = parseHttpDate(jsonObj.getString("timestamp"));
				
				MuteswanMessage msg = new MuteswanMessage(max,this,contentObj,date);
				msgs.add(msg);
				max--;
			}
		} catch (JSONException e) {
			MuteLog.Log("Circle", "getMsgRangeFromTor(): jsonString is not parseable: " + jsonString);
			return null;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return(msgs);
    	
	}

	private String parseHttpDate(String dateIn) {
		String date = null;
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		try {
			Date d = format.parse(dateIn);
			//date = d.getMonth()+1 + "/" + d.getDate() + " " + d.getHours() + ":" + d.getMinutes();
			SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss zzz" );
			
			Calendar cal = Calendar.getInstance();
			TimeZone tz = cal.getTimeZone();
	        df.setTimeZone( tz );
	        date = df.format(d);
		} catch (ParseException e) {
			return(null);
			// TODO Auto-generated catch block
			// e.printStackTrace();
		}
		return(date);
	}
	
	private MuteswanMessage parseMsgFromTor(Integer id, HttpResponse resp) throws org.apache.http.ParseException, IOException {

		
		String jsonString = EntityUtils.toString(resp.getEntity());
		if (jsonString == null) {
			MuteLog.Log("Circle", "WTF, jsonString is null");
			return null;
		}
		JSONObject jsonObj = null;
		try {
			jsonObj = new JSONObject(jsonString);
		} catch (JSONException e1) {
			MuteLog.Log("Circle", "WTF, jsonString is not parceable");
			return null;
		}
		
    	Header lastModified = resp.getFirstHeader("Last-Modified");
    	MuteswanMessage msg = null;
    	String date = null;
    	
    	if (lastModified == null) {
			MuteLog.Log("Circle", "WTF, lastModified is null");
			return null;
    	}
    
    	date = parseHttpDate(lastModified.getValue());
    	
    	try {
			msg = new MuteswanMessage(id,this,jsonObj,date);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
		
    	return(msg);
	}

	
	// returns 0 or greater on success, -1 and -2 on failure
	public Integer getLastTorMessageId() {
	  
		
	   HttpGet httpGet = new HttpGet("http://" + server + "/" + keyHash);
	   try {
	    HttpResponse resp = muteswanHttp.httpClient.execute(httpGet);
	   
	    String lastMessage = null;
	    String jsonString = EntityUtils.toString(resp.getEntity());
		if (jsonString == null) {
			Log.e("Circle", "getLastTorMessage(): jsonString is null");
			return null;
		}
		
		try {
			JSONObject jsonObj = new JSONObject(jsonString);
			lastMessage = jsonObj.getString("lastMessage");
		} catch (JSONException e) {
			Log.e("Circle", "unable to parse json message");
			return null;
		}

	    
	    if (lastMessage == null || lastMessage.equals("null")) {
	    	MuteLog.Log("LatestMessages","lastMessage header is null, indicates no messages posted yet.");
	    	return 0;
	    }
	    
	    MuteLog.Log("LatestMessages","getLastTorMessage(): lastmessage is " + lastMessage);
	    Integer result = Integer.parseInt(lastMessage);
	    if (result == null)
	    	return -1;
	    	
		return(result);

	} catch (ClientProtocolException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
	} 
	
	return(-1);
	
	}
	
	/*
	public MuteswanMessage getMsgLongpoll(Integer id) throws IOException {
		HttpGet httpGet = new HttpGet("http://" + server + "/" + keyHash + "/longpoll/" + id);
    	HttpResponse resp;
    	
    	MuteLog.Log("Circle", "getMsgLongpoll called for " + getShortname());
    	
		try {
			resp = muteswanHttp.httpClient.execute(httpGet);
			MuteswanMessage msg = parseMsgFromTor(id,resp);
			if (msg.signatures[0] != null) {
				   saveMsgToDb(id,msg.getDate(),msg.getMsg(),msg.signatures);
			} else {
				   saveMsgToDb(id,msg.getDate(),msg.getMsg());
			}
			return(msg);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return(null);
    	
	}*/
	
	

	public String getNotes() {
		return notes;
	}
	
	public String getServer() {
		return server;
	}
	
	public String getShortname() {
		return shortname;
	}
	
		
	
	
	
	
	
	// return the HTTP code, if IO error returns -1, protocol error -2, -3 key error 
	public Integer postMsg(JSONObject jsonObj) {
		
		HttpPost httpPost = new HttpPost("http://" + server + "/" + keyHash);
		ByteArrayEntity entity = new ByteArrayEntity(jsonObj.toString().getBytes());
		
		if (getPostPolicy() != null && !getPostPolicy().equals("ANY")) {
			if (getPostPolicy().equals("AUTHKEY")) {
				MuteLog.Log("Circle", "AUTHKEY Adding Signature header for " + getShortname());
				Signature sig = null;
				try {
					sig = Signature.getInstance("MD5WithRSA");
				} catch (NoSuchAlgorithmException e1) {
					// TODO Auto-generated catch block
					//e1.printStackTrace();
					return(-3);
				}
				IdentityStore idStore = new IdentityStore(context);
				RSAPrivateKey rsaPrivKey = null;
				for (Identity id : idStore) {
					if (id.publicKeyEnc.equals(getAuthKey())) {
						try {
							rsaPrivKey = id.getPrivateKey();
						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (InvalidKeySpecException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						break;
					}
				}
				
				if (rsaPrivKey == null) {
					Log.e("Circle", "Could not find appropriate identity for " + getShortname() + " in idstore.");
					return(-3);
				}
				
				   
				try {
					sig.initSign(rsaPrivKey);
					sig.update(jsonObj.toString().getBytes());
					//sig.update("some random sign data".getBytes("UTF8"));
					byte[] sigBytes = sig.sign();
					httpPost.setHeader("Signature",Base64.encodeBytes(sigBytes));
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SignatureException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				//circle.updateManifest(jsonObj,Base64.encodeBytes(sigBytes));
			} else if (getPostPolicy().equals("KEYLIST")) {
				MuteLog.Log("Circle", "KEYLIST Adding Signature header for " + getShortname());
				Signature sig = null;
				try {
					sig = Signature.getInstance("MD5WithRSA");
				} catch (NoSuchAlgorithmException e1) {
					// TODO Auto-generated catch block
					//e1.printStackTrace();
					return(-3);
				}
				
				IdentityStore idStore = new IdentityStore(context);
				RSAPrivateKey rsaPrivKey = null;
				KEYS: for (String key : keylist) {
					for (Identity id : idStore) {
					  if (key.equals(id.publicKeyEnc) && id.privateKeyEnc != null) {
						  MuteLog.Log("Circle", "Found identity " + id.getName());
						  try {

							rsaPrivKey = id.getPrivateKey();

						} catch (NoSuchAlgorithmException e) {
							e.printStackTrace();
							Log.e("Circle", "NoSuchAlgorithmException getting private key.");
						} catch (InvalidKeySpecException e) {
							// TODO Auto-generated catch block
							Log.e("Circle", "InvalidKeySpecException getting private key.");

						} catch (IOException e) {
							// TODO Auto-generated catch block
							Log.e("Circle", "IOException getting private key.");

						}
						  break KEYS;
					  }
				 }
				}
				
				if (rsaPrivKey == null) {
					Log.e("Circle", "Could not find appropriate identity for " + getShortname() + " in idstore.");
					return(-3);
				}
				

				try {
					sig.initSign(rsaPrivKey);
					sig.update(jsonObj.toString().getBytes());
					//sig.update("some random sign data".getBytes("UTF8"));
					
					byte[] sigBytes = sig.sign();

					httpPost.setHeader("Signature",Base64.encodeBytes(sigBytes));
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
					MuteLog.Log("Circle", "Invalid key posting.");
				} catch (SignatureException e) {
					// TODO Auto-generated catch block
					MuteLog.Log("Circle", "Signature exception posting.");
				}
				
			
		  }
		}
		
		
		httpPost.setEntity(entity);
		

		try {
			// POST MESSAGE
			HttpResponse response = muteswanHttp.httpClient.execute(httpPost);
			return(response.getStatusLine().getStatusCode());

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return(-2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return(-1);
		}
		
	}
	
	
	public Integer postMsg(String msg) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, JSONException {
		Crypto crypto = new Crypto(getKey().getBytes(), msg.getBytes());
		byte[] encData = crypto.encrypt();
		byte[] ivData = crypto.getIVData();
		MuteLog.Log("Circle", "iv data: " + ivData.toString());
		
		String base64EncData = Base64.encodeBytes(encData);
		String base64IVData = Base64.encodeBytes(ivData);
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("message", base64EncData);
		jsonObj.put("iv", base64IVData);
		
		return(postMsg(jsonObj));
	}
	
	public Integer postMsg(String msg, Identity[] identities) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, JSONException, InvalidKeyException, SignatureException, InvalidKeySpecException, IOException {
		Crypto crypto = new Crypto(getKey().getBytes(), msg.getBytes());
		byte[] encData = crypto.encrypt();
		
		String base64EncData = Base64.encodeBytes(encData);
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("message", base64EncData);
		
		JSONArray jsonArray = new JSONArray();
		
		
		
		for (int i=0; i<identities.length; i++) {
		    Signature sig = Signature.getInstance("MD5WithRSA");
		    
		    if (identities[i] == null) {
		    	MuteLog.Log("Circle", "Wtf, identities is null\n");
		    	break;
		    }
		    
		    MuteLog.Log("Circle", "Signing with " + identities[i].getName() + "\n");
		
		    RSAPrivateKey rsaPrivKey = identities[i].getPrivateKey();
		   
			sig.initSign(rsaPrivKey);
			sig.update(msg.getBytes("UTF8"));
			byte[] sigBytes = sig.sign();
			
			String sigLine = identities[i].pubKeyHash + ":" + Base64.encodeBytes(sigBytes);
			Crypto cryptoEnc = new Crypto(key.getBytes(),sigLine.getBytes("UTF8"));
			byte[] sigData = cryptoEnc.encrypt();
			jsonArray.put(Base64.encodeBytes(sigData));
		}
		
		jsonObj.put("signatures", jsonArray);
		
		
		return(postMsg(jsonObj));
		
	}
	
	public boolean msgExists(SQLiteDatabase db, Integer id) {
		String circleHash = Main.genHexHash(getFullText());
		Cursor cursor = db.query(OpenHelper.MESSAGESTABLE, new String[] { "date", "message" }, "msgId = ? and ringHash = ?", new String[] { id.toString(), circleHash }, null, null, "id desc" );
		if (cursor.getCount() != 0) {
			cursor.close();
			return(true);
		} else {
			cursor.close();
			return(false);
		}
	}
	
	// FIXME: should refactor
	public void saveMsgToDb(Integer id, String date, String msg) {
		MuteLog.Log("Circle","Saving message " + id);
		if (context == null) 
			return;	
		
		if (id == null || date == null || msg == null)
			return;
		
		String circleHash = Main.genHexHash(getFullText());
		SQLiteDatabase db = getOpenHelper().getWritableDatabase();
		if (msgExists(db,id)) {
			db.close();
			return;
		}

		SQLiteStatement insrt = db.compileStatement("INSERT INTO " + OpenHelper.MESSAGESTABLE + " (ringHash,msgId,date,message) VALUES (?,?,?,?)");
		insrt.bindString(1, circleHash);
		insrt.bindLong(2, id);
		insrt.bindString(3, date);
		insrt.bindString(4, msg);
		insrt.executeInsert();
		insrt.close();
		db.close();
		MuteLog.Log("Circle","Saved message " + id);
	}
	
	
	//FIXME: should refactor
	public void saveMsgToDb(Integer id, String date, String msg, String[] signatures) {
		if (context == null) 
			return;	
		
		String circleHash = Main.genHexHash(getFullText());
		SQLiteDatabase db = openHelper.getWritableDatabase();
		if (msgExists(db,id)) {
			db.close();
			return;
		}
		

		SQLiteStatement insrt = db.compileStatement("INSERT INTO " + OpenHelper.MESSAGESTABLE + " (ringHash,msgId,date,message) VALUES (?,?,?,?)");
		insrt.bindString(1, circleHash);
		insrt.bindLong(2, id);
		insrt.bindString(3, date);
		insrt.bindString(4, msg);
		insrt.executeInsert();
		insrt.close();
	
		for (int i=0; i<signatures.length; i++) {	
		  //FIXME: length for signatures
		  if (signatures[i] == null)
			  break;
		
		  insrt = db.compileStatement("INSERT INTO " + OpenHelper.SIGTABLE + " (msgId,ringHash,signature) VALUES (?,?,?)");
		  insrt.bindLong(1, id);
		  insrt.bindString(2, circleHash);
		  insrt.bindString(3,signatures[i]);
		  insrt.executeInsert();
		  insrt.close();
		}
		db.close();
	}
	
	
	

	@Override
	public String toString() {
		return getShortname();
	}

	public void createLastMessage(Integer curIndex, boolean closedb) {
		
		String circleHash = Main.genHexHash(getFullText());
		SQLiteDatabase db = openHelper.getWritableDatabase();
		SQLiteStatement insrt = db.compileStatement("INSERT INTO " + OpenHelper.LASTMESSAGES + " (ringHash,lastMessage,lastCheck) VALUES (?,?,datetime('now'))");
		insrt.bindString(1, circleHash);
		insrt.bindLong(2, curIndex);
		insrt.executeInsert();
		if (closedb)
		  db.close();
	}
	
	public void updateLastMessage(Integer curIndex, boolean closedb) {
		if (curIndex == null || curIndex < 0)
			return;
		else {
			curLastMsgId = curIndex;
		}
		SQLiteDatabase db = getOpenHelper().getWritableDatabase();
		saveLastMessage(db);
		
		if (closedb)
			db.close();
			
	}
	
	public void closedb() {
		if (openHelper != null) {
		 try {
		  openHelper.close();
		 } catch (android.database.sqlite.SQLiteException e) {
			 e.getCause();
		 }
		}
		//db.close();
	}
	
	public void saveLastMessage(SQLiteDatabase db) {
		String circleHash = Main.genHexHash(getFullText());
		
		
		SQLiteStatement update = db.compileStatement("UPDATE " + OpenHelper.LASTMESSAGES + " SET lastMessage = ?, lastCheck = datetime('now') WHERE ringHash = ?");
		update.bindLong(1, curLastMsgId);
		update.bindString(2, circleHash);
		update.execute();
		update.close();
		//if (update.execute() == -1) {
		//	SQLiteStatement insert = db.compileStatement("INSERT INTO " + OpenHelper.LASTMESSAGES + " (ringHash,lastMessage,lastCheck) VALUES(?,?,datetime('now'))");
		//	insert.bindString(1,circleHash);
		//	insert.bindLong(2, curLastMsgId);
		//	insert.executeInsert();
		//}
		//db.close();
	}

	public void updateManifest(JSONObject jsonObj) {
		HttpPut httpPut = new HttpPut("http://" + server + "/" + keyHash + "/manifest");
		ByteArrayEntity entity = new ByteArrayEntity(jsonObj.toString().getBytes());
		httpPut.setEntity(entity);
		
		try {
			muteswanHttp.httpClient.execute(httpPut);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return;
		}
	}
	
	public void updateManifest(JSONObject jsonObj, String signature) {
		HttpPut httpPut = new HttpPut("http://" + server + "/" + keyHash + "/manifest");
		ByteArrayEntity entity = new ByteArrayEntity(jsonObj.toString().getBytes());
		httpPut.setHeader("Signature",signature);
		httpPut.setEntity(entity);
		
		try {
			muteswanHttp.httpClient.execute(httpPut);
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return;
		}
	}

	public void downloadManifest() {
			HttpGet httpGet = new HttpGet("http://" + server + "/" + keyHash + "/manifest");
			MuteLog.Log("Circle", "Downloading manifest for " + getShortname());
	    	try {
				HttpResponse resp = muteswanHttp.httpClient.execute(httpGet);
				JSONObject jsonObj = parseManifest(resp);
				if (jsonObj == null || !jsonObj.has("manifest"))
					return;
				JSONObject jsonManifest = jsonObj.getJSONObject("manifest");
				
				if (jsonManifest.has("description"))
				  setDescription(new String(Base64.decode(jsonManifest.getString("description"))));
				if (jsonManifest.has("longdescription"))
				  setLongDescription(new String(Base64.decode(jsonManifest.getString("longdescription"))));
				if (jsonManifest.has("authkey"))
				  setAuthKey(jsonManifest.getString("authkey"));
				if (jsonManifest.has("postpolicy"))
				  setPostPolicy(jsonManifest.getString("postpolicy"));
				if (jsonManifest.has("image"))
				  setImage(Base64.decode(jsonManifest.getString("image")));
				if (jsonManifest.has("keylist")) {
					JSONArray keylist = jsonManifest.getJSONArray("keylist");
					String[] keylistArr = new String[keylist.length()];
					for (int i=0; i<keylist.length();i++) {
						keylistArr[i] = keylist.getString(i);
					}
					setKeylist(keylistArr);
				}
				
				
				saveManifestToDb();

		    	MuteLog.Log("Circle", "Downloaded manifest for " + getShortname());
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
   }
	
   
	
   private void saveManifestToDb() {
	    SQLiteDatabase db = openHelper.getWritableDatabase();
	    SQLiteStatement del = db.compileStatement("DELETE FROM " + OpenHelper.MANIFEST);
	    del.execute();
	    
		//SQLiteStatement insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (ringHash) VALUES (?)");
		//insrt.bindString(1, circleHash);
		//insrt.execute();
		
	    SQLiteStatement insrt;
		if (getDescription() != null) {
		  insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (key,value) VALUES('description',?)");
		  insrt.bindString(1, getDescription());
		  insrt.execute();
		}
		
		if (getLongDescription() != null) {
		  insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (key,value) VALUES('longdescription',?)");
	      insrt.bindString(1, getLongDescription());
		  insrt.execute();
		}
		
		if (getAuthKey() != null) {
	      insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (key,value) VALUES('authkey',?)");
		  insrt.bindString(1, getAuthKey());
		  insrt.execute();
		}
		
		if (getPostPolicy() != null) {
			insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (key,value) VALUES('postpolicy',?)");
			insrt.bindString(1, getPostPolicy());
			insrt.execute();
		}
		
		if (getImage() != null) {
			insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (key,value) VALUES('image',?)");
			insrt.bindBlob(1, getImage());
			insrt.execute();
		}
		
		if (getKeylist() != null) {
			for (String s : getKeylist()) {
				insrt = db.compileStatement("INSERT INTO " + OpenHelper.MANIFEST + " (key,value) VALUES('keylist',?)");
				insrt.bindString(1, s);
				insrt.execute();
			}
		}
		
		db.close();
	   
	}

private JSONObject parseManifest(HttpResponse resp) {
	   JSONObject jsonObj = null;
	   try {
		String jsonString = EntityUtils.toString(resp.getEntity());
		jsonObj = new JSONObject(jsonString);
	} catch (org.apache.http.ParseException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (JSONException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

	   return jsonObj;
   }




		
	


}
