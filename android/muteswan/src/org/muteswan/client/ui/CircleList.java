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
package org.muteswan.client.ui;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import org.json.JSONObject;
import org.muteswan.client.AlertDialogs;
import org.muteswan.client.GenerateCircle;
import org.muteswan.client.IMessageService;
import org.muteswan.client.MuteLog;
import org.muteswan.client.MuteswanHttp;
import org.muteswan.client.NewMessageService;
import org.muteswan.client.R;
import org.muteswan.client.Main;
import org.muteswan.client.data.Circle;
import org.muteswan.client.data.CircleStore;

import org.muteswan.client.data.MuteswanServer;
import org.muteswan.client.data.ServerList;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.PendingIntent;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class CircleList extends ListActivity {

	public static final String DELETED_CIRCLE_BROADCAST = "DELETEDCIRCLE";
	public static final String JOINED_CIRCLE_BROADCAST = "JOINEDCIRCLE";
	public static final String CREATED_CIRCLE_BROADCAST = "CREATEDCIRCLE";
	public static int SHARE = 0;
	public static int READ = 1;
	public static int WRITE = 2;
	public static int ANY = 3;
	public static int SCAN = 4;
	public static String[] actionPrompts = new String[] {
			"Select a circle to share.", "Select a circle to read messages.",
			"Select a circle to write a message.", "", "New circle added" };
	public Integer action;
	Bundle extra;
	public Circle[] circleList;
	private String initialText;
	private String newCircle;

	private CircleStore store;
	private CircleListAdapter listAdapter;

	private Builder writeNFC;
	private Builder beamNFC;
	private Builder receiveNFC;

	// private Builder shareSelection;

	@Override
	public void onResume() {
		super.onResume();

		sendBroadcast(new Intent(LatestMessages.CHECKING_MESSAGES));
		
		// bind to service
		Intent serviceIntent = new Intent(this,NewMessageService.class);
		bindService(serviceIntent,msgServiceConn,Context.BIND_AUTO_CREATE);

		
		
		if (currentlyBeaming) {
			beamNFCDlg.dismiss();
			// MuteLog.Log("CircleList", "Inside currently beaming.");
			// //nfcAdapter.disableForegroundNdefPush(this);
			// nfcAdapter.enableForegroundNdefPush(this,
			// createNdefMessage(circleList[selectedCirclePos].getFullText()));
		}

		extra = getIntent().getExtras();
		
		
		/*
		 * if (currentlyReceivingBeam) { MuteLog.Log("CircleList",
		 * "Inside receiving beaming."); Intent intent = getIntent();
		 * 
		 * Bundle extras = intent.getExtras(); Set<String> keys =
		 * extras.keySet(); for (String s : keys) { MuteLog.Log("CircleList",
		 * "Key: " + s); }
		 * 
		 * String actionstring = extras.getString("action"); String secretstring
		 * = extras.getString("secret"); MuteLog.Log("Secret: ", secretstring);
		 * MuteLog.Log("Action: ", secretstring);
		 * 
		 * Parcelable[] rawMsgs =
		 * intent.getParcelableArrayExtra(NfcAdapter.EXTRA_TAG); if (rawMsgs !=
		 * null) { NdefMessage msg = (NdefMessage) rawMsgs[0]; String newCircle
		 * = new String(msg.getRecords()[0].getPayload());
		 * MuteLog.Log("CircleList","New circle: " + newCircle);
		 * store.updateStore(newCircle); } else { MuteLog.Log("CircleList",
		 * "rawMsgs is null"); }
		 * 
		 * }
		 */

		store = new CircleStore(cipherSecret, this, true, false);
		circleList = getArray();
		// listAdapter = new ArrayAdapter<Circle>(this,
		// android.R.layout.simple_list_item_1, circleList);
		listAdapter = new CircleListAdapter();
		setListAdapter(listAdapter);

		Arrays.sort(circleList, comparatorCircles);
		listAdapter.notifyDataSetChanged();
	}

	public View.OnClickListener postClicked = new View.OnClickListener() {
		public void onClick(View v) {
			Intent intent = new Intent(getApplicationContext(),
					CircleList.class);
			intent.putExtra("action", CircleList.WRITE);
			intent.putExtra("secret", cipherSecret);
			startActivity(intent);
		}
	};

	public View.OnClickListener titleBarClicked = new View.OnClickListener() {
		public void onClick(View v) {
			Intent intent = new Intent(getApplicationContext(), Main.class);
			startActivity(intent);
		}
	};
	private AlertDialogs alertDialogs;
	private boolean shareManually;
	// private boolean useNFC;
	private Button addCircle;
	private Button createCircle;
	private String cipherSecret;
	private NfcAdapter nfcAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		extra = getIntent().getExtras();
		action = extra.getInt("action");
		newCircle = extra.getString("newCircle");
		initialText = extra.getString("initialText");
		cipherSecret = extra.getString("secret");

		nfcAdapter = getNFCAdapter();
		
		MuteLog.Log("CircleList", "Before CircleStore constructor.");
		store = new CircleStore(cipherSecret, this, true, false);
		MuteLog.Log("CircleList", "After CircleStore constructor.");

		setContentView(R.layout.circlelist);

		SharedPreferences defPrefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		shareManually = defPrefs.getBoolean("allowManualJoining", false);
		// NFCFIX
		// useNFC = defPrefs.getBoolean("useNFC", false);

		// if (useNFC) {
		// nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		// }

		TextView txt = (TextView) findViewById(R.id.android_circlelistprompt);
		// RelativeLayout circleListButtons = (RelativeLayout)
		// findViewById(R.id.circlelistButtons);
		txt.setText(actionPrompts[action]);

		final ImageView titleBarImage = (ImageView) findViewById(R.id.titlebarImage);
		titleBarImage.setOnClickListener(titleBarClicked);

		if (action == SCAN) {
			Intent intent = new Intent("com.google.zxing.client.android.SCAN");
			intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
			startActivityForResult(intent, 0);
		}

		addCircle = (Button) findViewById(R.id.android_circlelistAddCircle);
		createCircle = (Button) findViewById(R.id.android_circlelistCreateCircle);
		addCircle.setOnClickListener(addCircleListener);
		createCircle.setOnClickListener(createCircleListener);

		circleList = getArray();
		registerForContextMenu(getListView());

		alertDialogs = new AlertDialogs(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();

		if (shareManually) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.circlelist, menu);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.toString().equals("Manual Join")) {
			addCircleManuallyDialog();
		}
		return true;

	}

	private ComparatorCircles comparatorCircles = new ComparatorCircles();

	class ComparatorCircles implements Comparator<Circle> {
		public int compare(Circle obj1, Circle obj2) {
			Circle circle1 = obj1;
			Circle circle2 = obj2;

			if (newCircle != null && circle1.getShortname().equals(newCircle)) {
				return (-1);
			}

			if (newCircle != null && circle2.getShortname().equals(newCircle)) {
				return (1);
			}

			return (circle1.getShortname().compareTo(circle2.getShortname()));
		}
	}

	// FIXME: should be part of store
	private Circle[] getArray() {

		Circle[] circleList = new Circle[store.size()];
		int i = 0;
		for (Circle r : store) {
			circleList[i] = r;
			i++;
		}

		return (circleList);
	}

	public class CircleListAdapter extends BaseAdapter {

		@Override
		public int getCount() {

			return (circleList.length);

		}

		@Override
		public Object getItem(int position) {

			return (circleList[position]);
		}

		@Override
		public long getItemId(int position) {
			return (position);
		}

		public View.OnClickListener circleClicked = new View.OnClickListener() {
			public void onClick(View v) {
				Integer position = (Integer) v
						.getTag(R.id.android_circleListName);
				RelativeLayout innerLayout = (RelativeLayout) v
						.findViewById(R.id.circleListInnerEntryLayout);
				// on orientation change this may be null in some cases
				if (innerLayout != null)
					innerLayout.setBackgroundColor(R.drawable.darkerborder);
				dispatchActivity(position);
			}
		};

		public View.OnClickListener circleShareClicked = new View.OnClickListener() {
			public void onClick(View v) {
				Integer position = (Integer) v.getTag(R.id.circleListShare);
				shareCircle(position);
			}
		};
		public View.OnClickListener circleDeleteClicked = new View.OnClickListener() {
			public void onClick(View v) {
				final Integer position = (Integer) v
						.getTag(R.id.circleListDelete);

				AlertDialog.Builder builder = new AlertDialog.Builder(
						CircleList.this);

				builder.setMessage(
						getString(R.string.delete_circle_ask_notif_prefix)
								+ circleList[position].getShortname()
								+ getString(R.string.delete_circle_ask_notif_suffix))
						// builder.setMessage(R.string.q_delete_circle)
						.setCancelable(false)
						.setPositiveButton(R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										deleteCircle(position);
									}
								})
						.setNegativeButton(R.string.no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.cancel();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();

			}

		};

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			RelativeLayout layout = (RelativeLayout) convertView;
			if (layout == null)
				layout = (RelativeLayout) getLayoutInflater().inflate(
						R.layout.circlelistentry, parent, false);

			layout.setTag(R.id.android_circleListName, position);

			TextView txtCircle = (TextView) layout
					.findViewById(R.id.android_circleListName);
			txtCircle.setClickable(true);
			txtCircle.setTag(R.id.android_circleListName, position);
			txtCircle.setOnClickListener(circleClicked);

			if (newCircle != null
					&& circleList[position].getShortname().equals(newCircle)) {
				txtCircle.setText(circleList[position].getShortname()
						+ getString(R.string.new_circle_indication));
			} else {
				txtCircle.setText(circleList[position].getShortname());
			}

			layout.setClickable(true);
			layout.setOnClickListener(circleClicked);

			ImageView shareCircleButton = (ImageView) layout
					.findViewById(R.id.circleListShare);
			ImageView deleteCircleButton = (ImageView) layout
					.findViewById(R.id.circleListDelete);
			if (action == ANY) {

				shareCircleButton.setClickable(true);
				shareCircleButton.setTag(R.id.circleListShare, position);
				shareCircleButton.setOnClickListener(circleShareClicked);

				deleteCircleButton.setClickable(true);
				deleteCircleButton.setTag(R.id.circleListDelete, position);
				deleteCircleButton.setOnClickListener(circleDeleteClicked);

			} else {
				shareCircleButton.setVisibility(View.GONE);
				deleteCircleButton.setVisibility(View.GONE);

			}

			return layout;
		}

	}

	public View.OnClickListener listItemClicked = new View.OnClickListener() {

		@Override
		public void onClick(View v) {

		}

	};

	protected void onListItemClick(ListView parent, View v, int position,
			long id) {

		dispatchActivity(position);

	}

	private void dispatchActivity(int position) {
		Intent intent = null;
		if (action == WRITE) {
			intent = new Intent(getApplicationContext(), WriteMsg.class);
			intent.putExtra("circle", circleList[position].getFullText());
		} else if (action == READ || action == ANY) {
			intent = new Intent(getApplicationContext(), LatestMessages.class);
			intent.putExtra("circle",
					Main.genHexHash(circleList[position].getFullText()));
			intent.putExtra("secret", cipherSecret);

		} else if (action == SHARE) {
			intent = new Intent("com.google.zxing.client.android.ENCODE");
			intent.putExtra("ENCODE_DATA", circleList[position].getFullText());
			;
			intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
			try {
				startActivity(intent);
			} catch (ActivityNotFoundException e) {
				alertDialogs.offerToInstallBarcodeScanner();

			}
			return;
		} else if (action == SCAN) {
			return;
		}

		intent.putExtra("initialText", initialText);
		// MuteLog.Log("CircleList", "Would launch " + action.toString());
		startActivity(intent);

	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.circlelistcontextmenu, menu);
	}

	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		switch (item.getItemId()) {
		case R.id.circleListDelete:
			deleteCircle(info.position);
			break;
		case R.id.circleListRead:
			showMsgList(info.position);
			break;
		case R.id.circleListShare:
			shareCircle(info.position);
			break;
		// MANIFEST features not used right now
		// case R.id.circleListView:
		// viewCircle(info.position);
		// break;
		// case R.id.circleListEdit:
		// editCircle(info.position);
		// break;
		case R.id.circleListWriteMsg:
			writeMsg(info.position);
			break;
		}
		return true;

	}

	private Button.OnClickListener addCircleListener = new Button.OnClickListener() {

		public void onClick(View v) {

			showJoinSelection();

		}
	};

	private void joinNFC() {
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass())
						.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		IntentFilter tagDetected = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);
		IntentFilter techDetected = new IntentFilter(
				NfcAdapter.ACTION_TECH_DISCOVERED);
		IntentFilter beamDetected = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);
		// IntentFilter[] tagFilters = new IntentFilter[] { tagDetected };

		IntentFilter[] filters = new IntentFilter[] { beamDetected,
				tagDetected, techDetected };

		// nfcAdapter.enableForegroundDispatch(this, pendingIntent, tagFilters,
		// null);
		nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);

		// nfcAdapter.enableForegroundNdefPush(this,
		// createNdefMessage(circleList[selectedCirclePos].getFullText()));
		readyToReceiveNFC();
	}


	final Handler handleJoinNFC = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			joinNFC();

		}
	};

	final Handler doneBeamingNFC = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			cleanupSendingNFC();

		}
	};

	final Handler doneReceivingNFC = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			cleanupReceivingNFC();

		}
	};

	private void cleanupReceivingNFC() {
		currentlyReceivingNFC = false;
		nfcAdapter.disableForegroundNdefPush(this);
		nfcAdapter.disableForegroundDispatch(this);
	}

	private void cleanupSendingNFC() {
		currentlyBeaming = false;
		currentlyWritingTag = false;
		nfcAdapter.disableForegroundNdefPush(this);
		nfcAdapter.disableForegroundDispatch(this);
	}

	private void addCircleManuallyDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(CircleList.this);
		LayoutInflater layoutInflater = (LayoutInflater) getApplicationContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = layoutInflater.inflate(R.layout.addcirclemanually, null);

		builder.setView(view);

		builder.setMessage(R.string.t_join_circle_manually);
		final EditText editTxt = (EditText) view
				.findViewById(R.id.circleListManualJoinCircle);

		builder.setPositiveButton(R.string.add_circle_confirm_yes,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {

						// FIXME refactor
						String circleTxt = editTxt.getText().toString();

						CircleStore store = new CircleStore(cipherSecret,
								getApplicationContext(), true, false);
						Circle circle = new Circle(cipherSecret,
								getApplicationContext(), circleTxt);
						if (circle.getShortname() == null)
							MuteLog.Log("CircleList",
									"Circle is null after initializing manually.");
						store.updateStore(circleTxt);

						Intent joinCircleIntent = new Intent(
								CircleList.JOINED_CIRCLE_BROADCAST);
						joinCircleIntent.putExtra("circle",
								Main.genHexHash(circle.getFullText()));
						sendBroadcast(joinCircleIntent);

						newCircle = circle.getShortname();
						onResume();

					}
				});
		builder.setNegativeButton(R.string.add_circle_confirm_cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
		builder.show();
	}

	private Button.OnClickListener createCircleListener = new Button.OnClickListener() {
		public void onClick(View v) {

			
			final ServerList serverList = new ServerList();
			serverList.init(getApplicationContext());
			
			AlertDialog.Builder builder = new AlertDialog.Builder(
					CircleList.this);
			LayoutInflater factory = LayoutInflater.from(CircleList.this);
			final View textEntryView = factory.inflate(R.layout.circlecreate,
					null);
			
			final EditText circleNameEditText = (EditText) textEntryView
					.findViewById(R.id.circleName);
			final Spinner createCircleServer = (Spinner) textEntryView.findViewById(R.id.createCircleServer);
			
			//createCircleServer.setBackgroundColor(android.R.color.background_dark);

			
			
			
			
			ArrayAdapter<MuteswanServer> serverListAdapter = new ArrayAdapter<MuteswanServer>(getApplicationContext(),
					//android.R.layout.simple_spinner_dropdown_item, serverList.getArray());
					R.layout.serverlistspinner, serverList.getArray());
			createCircleServer.setAdapter(serverListAdapter);
			
			
			

			builder.setMessage(R.string.t_create_circle)
					.setCancelable(false)
					.setView(textEntryView)
					.setPositiveButton(R.string.create_circle_confirm_yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {

									
									MuteswanServer selectedMuteswanServer = (MuteswanServer) createCircleServer.getSelectedItem();
									GenerateCircle genCircle = new GenerateCircle(
											cipherSecret,
											getApplicationContext(),
											circleNameEditText.getText().toString(),
											selectedMuteswanServer.getHostname());

									genCircle.saveCircle();
									genCircle.broadcastCreate();
									//BOOK
									onResume();

									// Intent intent = new
									// Intent(getApplicationContext(),CircleList.class);
									// startActivity(intent);

									newCircle = circleNameEditText.getText()
											.toString();
									onResume();
								}
							})
					.setNegativeButton(R.string.add_circle_confirm_cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			AlertDialog alert = builder.create();
			alert.show();

			return;
		}
	};
	private boolean currentlyWritingTag = false;
	private Integer selectedCirclePos;
	private boolean currentlyBeaming = false;
	private boolean currentlyReceivingNFC = false;
	private AlertDialog writeNFCDlg;
	private AlertDialog beamNFCDlg;
	private AlertDialog receiveNFCDlg;
	protected IMessageService msgService;

	private void readyToBeamNFC() {
		beamNFC = new AlertDialog.Builder(this);
		beamNFC.setTitle("Ready to Beam NFC Tag");
		beamNFC.setMessage("You should now be beaming the NFC to another device. Click the button below to stop.");
		beamNFC.setPositiveButton("Done",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
						doneBeamingNFC.sendEmptyMessage(0);
					}
				});
		beamNFC.create();
		beamNFCDlg = beamNFC.show();
	}

	private void readyToWriteNFC() {
		writeNFC = new AlertDialog.Builder(this);
		writeNFC.setTitle("Ready to Write to NFC Tag");
		writeNFC.setMessage("You should now be ready to write to an NFC tag. Press OK to stop.");
		writeNFC.setPositiveButton("Done",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
						doneBeamingNFC.sendEmptyMessage(0);
					}
				});
		writeNFC.create();
		writeNFCDlg = writeNFC.show();
	}

	public void readyToReceiveNFC() {
		receiveNFC = new AlertDialog.Builder(this);
		receiveNFC.setTitle("Ready to Receive NFC Data");
		receiveNFC
				.setMessage("You should be ready to receive NFC data. Click the button below to stop NFC detection.");
		receiveNFC.setPositiveButton("Done",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialogInterface, int i) {
						doneReceivingNFC.sendEmptyMessage(0);

					}
				});
		receiveNFC.create();
		receiveNFCDlg = receiveNFC.show();
	}

	private boolean writeNdefMessage(Ndef ndef, NdefMessage ndefMsg) {
		
		try {
			ndef.connect();
			ndef.writeNdefMessage(ndefMsg);
		} catch (IOException e) {
			MuteLog.Log("CircleList", "Failed to write ndef message!");
			e.printStackTrace();
			return false;
		} catch (FormatException e) {
			MuteLog.Log("CircleList", "Message isn't formated...why didn't we already?");
			e.printStackTrace();
			return false;
		}
		
		
		return true;
	}
	
	public void onNewIntent(Intent intent) {

		MuteLog.Log("CircleList", "On New intent...");
		if (currentlyWritingTag) {
			currentlyWritingTag = false;

			NdefMessage ndefMsg = createNdefMessage(circleList[selectedCirclePos]
					.getFullText(),true);
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			Ndef ndef = Ndef.get(tag);
			
			
			
			
			if (ndef != null) {
				
				// ndefMsg contains the Android AAR (launches Market/Muteswan)
				if (writeNdefMessage(ndef,ndefMsg)) {
					writeNFCDlg.dismiss();
				// the AAR may be too big, lets try without it
				} else {
					ndefMsg = createNdefMessage(circleList[selectedCirclePos]
							.getFullText(),false);
					if (writeNdefMessage(ndef,ndefMsg)) {
						writeNFCDlg.dismiss();
					} else {
						MuteLog.Log("CircleList",
							"IO exception writing ndef message.");
					    writeNFCDlg.dismiss();
					    Builder errorWriting = new AlertDialog.Builder(this);
					    errorWriting.setTitle("Error writing to tag");
					    errorWriting
							.setMessage("Sorry, the circle was not written to the tag. It may be too large.");
					    errorWriting.setPositiveButton("Done",
							new DialogInterface.OnClickListener() {
								public void onClick(
										DialogInterface dialogInterface, int i) {

								}
							});
					    errorWriting.create();
					    errorWriting.show();
					} 
				}
				
			} else {
			  
			  MuteLog.Log("CircleList", "Ndef is null, probably not formatted.");
			  NdefFormatable ndefF = NdefFormatable.get(tag);
			  try {
				ndefF.connect();
				ndefF.format(ndefMsg);
				writeNFCDlg.dismiss();
			  } catch (IOException e) {
				MuteLog.Log("CircleList", "IO exception formatting.");
				e.printStackTrace();
			  } catch (FormatException e) {
				MuteLog.Log("CircleList", "Format exception formatting!");
				e.printStackTrace();
			  }
			
			}
		}

		if (currentlyReceivingNFC) {

			Parcelable[] rawMsgs = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (rawMsgs != null) {
				NdefMessage msg = (NdefMessage) rawMsgs[0];
				String circleData = new String(msg.getRecords()[0].getPayload());
				MuteLog.Log("CircleList", "New circle: " + circleData);
				Circle circle = new Circle(cipherSecret,
						getApplicationContext(), circleData);
				if (!store.containsShortname(circle.getShortname())) {
				   store.updateStore(circleData);
				   String circleKey = Main.genHexHash(circleData);
				   newCircle = store.asHashMap().get(circleKey).getShortname();
				   receiveNFCDlg.dismiss();
				} else {
					receiveNFCDlg.dismiss();
					alertDialogs.duplicateShortName(circle.getShortname());
					
				}
					
			} else {
				MuteLog.Log("CircleList", "rawMsgs is null");
			}
		}

	}

	private NdefMessage createNdefMessage(String circleText, Boolean useAAR) {
		/*
		 * byte[] mimeBytes; try { mimeBytes =
		 * "application/muteswan.circle".getBytes("UTF-8"); } catch
		 * (UnsupportedEncodingException e) { return null; }
		 * 
		 * // Example with proper API //NdefRecord ndefRec =
		 * NdefRecord.createApplicationRecord("com.example.android.beam")};
		 * 
		 * NdefRecord rec = new
		 * NdefRecord(NdefRecord.TNF_MIME_MEDIA,mimeBytes,new byte[0],
		 * circleList[selectedCirclePos].getFullText().getBytes());
		 */

		// adding an app record hard core style
		NdefRecord appRec = new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE,
				"android.com:pkg".getBytes(), new byte[0],
				"org.muteswan.client".getBytes());
		NdefRecord rec = new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE,
				"muteswan.org:C".getBytes(), new byte[0],
				circleList[selectedCirclePos].getFullText().getBytes());
		//
		if (useAAR) {
			return new NdefMessage(new NdefRecord[] { rec, appRec });
		} else {
			return new NdefMessage(new NdefRecord[] { rec });
		}

	}

	private void deleteCircle(int position) {
		CircleStore store = new CircleStore(cipherSecret,
				getApplicationContext());
		store.deleteCircle(circleList[position]);

		Intent deleteCircleIntent = new Intent(
				CircleList.DELETED_CIRCLE_BROADCAST);
		deleteCircleIntent.putExtra("circle",
				Main.genHexHash(circleList[position].getFullText()));
		deleteCircleIntent.putExtra("secret", cipherSecret);
		sendBroadcast(deleteCircleIntent);

		Toast.makeText(
				this,
				getString(R.string.deleted_circle_notif_prefix)
						+ circleList[position].getShortname()
						+ getString(R.string.delete_circle_notif_suffix),
				Toast.LENGTH_LONG).show();
		onResume();
	}

	private void writeMsg(Integer position) {
		Intent intent = new Intent(getApplicationContext(), WriteMsg.class);
		intent.putExtra("circle", circleList[position].getFullText());
		startActivity(intent);
	}


	private NfcAdapter getNFCAdapter() {
		try {
			NfcAdapter nfc = NfcAdapter
					.getDefaultAdapter(getApplicationContext());
			return nfc;
		} catch (java.lang.NoClassDefFoundError e) {
			MuteLog.Log("CircleList", "NFC is not supported in in this verion of Android.");
			return null;
		}
	}
	
	private void startScan() {
		Intent intent = new Intent(
				"com.google.zxing.client.android.SCAN");
		intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
		try {
			startActivityForResult(intent, 0);
		} catch (ActivityNotFoundException e) {
			alertDialogs.offerToInstallBarcodeScanner();
		}
	}
	
	private void showJoinSelection() {
		
		
		if (nfcAdapter == null) {
			startScan();
			return;
		}
		
		final CharSequence[] items = { "Scan QR Code", "NFC" };
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Join");
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				if (item == 0) {
					startScan();
				} else if (item == 1) {
					try {
						nfcAdapter = NfcAdapter
							.getDefaultAdapter(getApplicationContext());
					} catch (java.lang.NoClassDefFoundError e) {
						MuteLog.Log("CircleList", "NFC not supported!");
						
						return;
					}
					currentlyReceivingNFC = true;
					handleJoinNFC.sendEmptyMessage(0);
				}
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void showQRCode(int position, boolean serverOnly) {
		Intent intent = new Intent(
				"com.google.zxing.client.android.ENCODE");
		if (!serverOnly) {
		  intent.putExtra("ENCODE_DATA",
				circleList[position].getFullText());
		} else {
			intent.putExtra("ENCODE_DATA",
					circleList[position].getServer());
		}
		intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
		intent.putExtra("ENCODE_SHOW_CONTENTS", true);
		try {
			startActivityForResult(intent, 0);
		} catch (ActivityNotFoundException e) {
			alertDialogs.offerToInstallBarcodeScanner();
		}
	}
	
	private void showShareSelection(final int position) {
		
		if (nfcAdapter == null) {
			//showQRCode(position,false);
			final CharSequence[] items = { "Share with QR Code"
					 };
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			
			builder.setTitle("Share " + circleList[position].getShortname());
			builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					if (item == 0) {
						showQRCode(position,false);
					} else if (item == 1) {
						showQRCode(position,true);
					}
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
			return;
		} else {
			final CharSequence[] items = { "Share with QR Code",
					"Write to an NFC tag", "Beam to an Android device",};
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Share " + circleList[position].getShortname());
			
			builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					if (item == 0) {
						showQRCode(position,false);
					} else if (item == 1) {
						nfcAdapter = NfcAdapter
								.getDefaultAdapter(getApplicationContext());
						shareWriteNFCTag(position);
					} else if (item == 2) {
						nfcAdapter = NfcAdapter
								.getDefaultAdapter(getApplicationContext());
						shareBeamNFC(position);
					} else if (item == 3) {
						showQRCode(position,true);
					}
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
			return;
		}
		
		
	}

	private void shareWriteNFCTag(int position) {

		currentlyWritingTag = true;
		// currentlyBeaming = true;
		selectedCirclePos = position;

		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass())
						.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		IntentFilter tagDetected = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);
		IntentFilter techDetected = new IntentFilter(
				NfcAdapter.ACTION_TECH_DISCOVERED);
		IntentFilter beamDetected = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);

		IntentFilter[] filters = new IntentFilter[] { beamDetected,
				tagDetected, techDetected };

		nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
		// alertDialogs.readyToWriteNFCTag();
		readyToWriteNFC();

		// nfcAdapter.enableForegroundNdefPush(this,
		// createNdefMessage(circleList[selectedCirclePos].getFullText()));
		// readyToBeamNFC();

	}

	private void shareBeamNFC(int position) {

		// currentlyWritingTag = true;
		currentlyBeaming = true;
		selectedCirclePos = position;

		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass())
						.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		IntentFilter tagDetected = new IntentFilter(
				NfcAdapter.ACTION_TAG_DISCOVERED);
		IntentFilter techDetected = new IntentFilter(
				NfcAdapter.ACTION_TECH_DISCOVERED);
		IntentFilter beamDetected = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);

		IntentFilter[] filters = new IntentFilter[] { beamDetected,
				tagDetected, techDetected };

		nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
		// alertDialogs.readyToWriteNFCTag();

		nfcAdapter.enableForegroundNdefPush(this,
				createNdefMessage(circleList[selectedCirclePos].getFullText(),true));
		readyToBeamNFC();

	}

	private void shareCircle(Integer position) {
		showShareSelection(position);
	}

	public void onPause() {
		if (nfcAdapter != null) {
			nfcAdapter.disableForegroundNdefPush(this);
			nfcAdapter.disableForegroundDispatch(this);
		}
		super.onPause();
	}

	private void showMsgList(Integer position) {
		Intent intent = new Intent(getApplicationContext(),
				LatestMessages.class);
		intent.putExtra("circle",
				Main.genHexHash(circleList[position].getFullText()));
		intent.putExtra("secret", cipherSecret);
		MuteLog.Log("CircleList", "Set secret .. " + cipherSecret);
		startActivity(intent);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// if (requestCode == 0) {
		if (requestCode == 0 && resultCode == RESULT_OK) {
			// Handle successful scan
			String contents = intent.getStringExtra("SCAN_RESULT");

			int atIndex = contents.indexOf("@");

			// RING
			if (atIndex != -1) {

				CircleStore store = new CircleStore(cipherSecret,
						getApplicationContext(), true, false);
				Circle circle = new Circle(cipherSecret,
						getApplicationContext(), contents);
				if (!store.containsShortname(circle.getShortname())) {
				  store.updateStore(contents);

				  Intent joinCircleIntent = new Intent(
						CircleList.JOINED_CIRCLE_BROADCAST);
				  joinCircleIntent.putExtra("circle",
						Main.genHexHash(circle.getFullText()));
				  sendBroadcast(joinCircleIntent);

				  newCircle = circle.getShortname();
				  
				   try {
						msgService.updateServerList(true);
				   } catch (RemoteException e) {
						MuteLog.Log("CircleList", "Failed to update server list: " + e);
				   }
				} else {
					alertDialogs.duplicateShortName(newCircle);
				}

				
			// just a server
			} else if (contents.endsWith(".onion")) {
				String srvName = contents;
				if (contents.contains("http://")) {
					srvName = contents.replace("http://", "");
				}
				
				ServerList serverList = new ServerList();
				serverList.init(getApplicationContext());
				MuteswanServer server = new MuteswanServer();
				server.init(srvName, new JSONObject());
				serverList.addServer(server);
				
				
				AlertDialog.Builder builder = new AlertDialog.Builder(
						CircleList.this);

				builder.setMessage("Added server: " + srvName);
				builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
							int id) {
							try {
								msgService.updateServerList(true);
							} catch (RemoteException e) {
								MuteLog.Log("CircleList", "Failed to update server list: " + e);
							}
					}
				});
				
				AlertDialog alert = builder.create();
				alert.show();
				
				
				
			}

		}

	}

	
	public void onDestroy() {
		super.onDestroy();
		
		if (msgService != null) {
			unbindService(msgServiceConn);
		}
	}
	
	private ServiceConnection msgServiceConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName className,
             IBinder service) {
     	msgService = IMessageService.Stub.asInterface(service);
     	try {
				msgService.setSkipNextCheck(true);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
     	MuteLog.Log("LatestMessages", "onServiceConnected called.");
     	if (msgService == null) {
     		Log.e("LatestMessages", "msgService is null ");
     	}

     }

     public void onServiceDisconnected(ComponentName className) {
        msgService = null;
     }
  };
	
	
}
