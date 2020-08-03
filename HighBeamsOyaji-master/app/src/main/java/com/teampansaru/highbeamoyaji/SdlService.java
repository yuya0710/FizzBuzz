package com.teampansaru.highbeamoyaji;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.TTSChunkFactory;
import com.smartdevicelink.proxy.rpc.Alert;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.HeadLampStatus;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.SetDisplayLayout;
import com.smartdevicelink.proxy.rpc.Speak;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedWindows;
import com.smartdevicelink.proxy.rpc.enums.Result;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.DebugTool;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class SdlService extends Service {

	private static final String TAG 					= "SDL Service";

	private static final String APP_NAME 				= "Hello Sdl";
	private static final String APP_NAME_ES 			= "Hola Sdl";
	private static final String APP_NAME_FR 			= "Bonjour Sdl";
	private static final String APP_ID 					= "8678309";

	private static final String ICON_FILENAME 			= "hello_sdl_icon.png";
	private static final int FOREGROUND_SERVICE_ID = 111;

	//Manticoreで指定されるポート番号を設定します
	private static final int TCP_PORT = 10302;
	private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

	// variable to create and call functions of the SyncProxy
	private SdlManager sdlManager = null;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			enterForeground();
		}
	}

	// Helper method to let the service enter foreground mode
	@SuppressLint("NewApi")
	public void enterForeground() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
				Notification serviceNotification = new Notification.Builder(this, channel.getId())
						.setContentTitle("Connected through SDL")
						.setSmallIcon(R.drawable.ic_sdl)
						.build();
				startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startProxy();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

		if (sdlManager != null) {
			sdlManager.dispose();
		}

		super.onDestroy();
	}

	private void startProxy() {
		// This logic is to select the correct transport and security levels defined in the selected build flavor
		// Build flavors are selected by the "build variants" tab typically located in the bottom left of Android Studio
		// Typically in your app, you will only set one of these.
		if (sdlManager == null) {
			Log.i(TAG, "Starting SDL Proxy");
			// Enable DebugTool for debug build type
			if (BuildConfig.DEBUG){
				DebugTool.enableDebugTool();
			}
			BaseTransportConfig transport = null;
			if (BuildConfig.TRANSPORT.equals("MULTI")) {
				int securityLevel;
				if (BuildConfig.SECURITY.equals("HIGH")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
				} else if (BuildConfig.SECURITY.equals("MED")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
				} else if (BuildConfig.SECURITY.equals("LOW")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
				} else {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
				}
				transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
			} else if (BuildConfig.TRANSPORT.equals("TCP")) {
				transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
			} else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
				MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
				mtc.setRequiresHighBandwidth(true);
				transport = mtc;
			}

			// The app type to be used
			Vector<AppHMIType> appType = new Vector<>();
			appType.add(AppHMIType.DEFAULT);

			// The manager listener helps you know when certain events that pertain to the SDL Manager happen
			// Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
			SdlManagerListener listener = new SdlManagerListener() {
				@Override
				public void onStart() {
					// HMIのステータスが変わった場合の呼び出されます。
					sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
						@Override
						public void onNotified(RPCNotification notification) {

							OnHMIStatus onHMIStatus = (OnHMIStatus)notification;
							if (onHMIStatus.getWindowID() != null && onHMIStatus.getWindowID() != PredefinedWindows.DEFAULT_WINDOW.getValue()) {
								return;
							}
							if (onHMIStatus.getHmiLevel() == HMILevel.HMI_FULL && onHMIStatus.getFirstRun()) {
								registDisplayLayout();
							}
						}
					});

					//車輌データに変更があった場合は、このListenerが呼び出されます。
					//更新があった項目のみのデータが飛んでくるためデータのNULLチェックは必ず実施すること
					sdlManager.addOnRPCNotificationListener(FunctionID.ON_VEHICLE_DATA, new OnRPCNotificationListener() {
						@Override
						public void onNotified(RPCNotification notification) {
							OnVehicleData vehicleData = (OnVehicleData) notification;

							HeadLampStatus vehicleDataHighBeams = vehicleData.getHeadLampStatus();

							Log.d(MainActivity.LOG,"はいびーむ"+vehicleDataHighBeams.getHighBeamsOn());

							Intent intent = new Intent("HIGH_BEAM");
							intent.putExtra(MainActivity.ISTURNEDONHIGHBEAM, vehicleDataHighBeams.getHighBeamsOn());
							sendBroadcast(intent);
						}
					});

				}

				@Override
				public void onDestroy() {
					SdlService.this.stopSelf();
				}

				@Override
				public void onError(String info, Exception e) {
				}

				@Override
				public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(Language language){
					String appName;
					switch (language) {
						case ES_MX:
							appName = APP_NAME_ES;
							break;
						case FR_CA:
							appName = APP_NAME_FR;
							break;
						default:
							return null;
					}

					return new LifecycleConfigurationUpdate(appName,null,TTSChunkFactory.createSimpleTTSChunks(appName), null);
				}
			};

			// Create App Icon, this is set in the SdlManager builder
			SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.drawable.icon, true);

			// The manager builder sets options for your session
			SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
			builder.setAppTypes(appType);
			builder.setTransportType(transport);
			builder.setAppIcon(appIcon);
			sdlManager = builder.build();
			sdlManager.start();
		}
	}


	/**
	 * 画面を登録する
	 */
	private void registDisplayLayout() {

		SetDisplayLayout setDisplayLayout = new SetDisplayLayout();

		//テンプレートの種類を「NON_MEDIA」に設定する
		//https://smartdevicelink.com/en/guides/sdl-overview-guides/user-interface/supported-templates/#non-media
		setDisplayLayout.setDisplayLayout(PredefinedLayout.NON_MEDIA.toString());

		//テンプレートの設定のResponseイベント
		setDisplayLayout.setOnRPCResponseListener(new OnRPCResponseListener() {

			/**
			 * テンプレート設定のResponse
			 * @param correlationId
			 * @param response
			 */
			@Override
			public void onResponse(int correlationId, RPCResponse response) {

				//登録に成功したかどうか
				if(!response.getSuccess()) {
					Log.e(TAG, "displaylayout response false");
					return;
				}

				//NON MEDIAの画面要素にデータを設定していく
				sdlManager.getScreenManager().beginTransaction();

				sdlManager.getScreenManager().setTextField1("Speed");
				sdlManager.getScreenManager().setTextField3("--- km/h");
				sdlManager.getScreenManager().setTextField2("PRNDL");
				sdlManager.getScreenManager().setTextField4("--.");



				//右側の画像を設定する
				SdlArtwork artwork = new SdlArtwork("artwork01.png", FileType.GRAPHIC_PNG, R.drawable.artwork01, true);
				sdlManager.getScreenManager().setPrimaryGraphic(artwork);

				//画面の設定を完了する
				sdlManager.getScreenManager().commit(new CompletionListener() {
					/**
					 * 画面の設定が完了したかどうか
					 * @param success
					 */
					@Override
					public void onComplete(boolean success) {
						Log.i(TAG, "ScreenManager update complete: " + success);
						registVehicleData();
					}
				});

				//一つ目のボタン（テキストだけのボタンを設定）
				SoftButtonState textState = new SoftButtonState("text_button", "TEXT BUTTON", null);
				//ボタンを作成する
				SoftButtonObject textButtonObject = new SoftButtonObject(textState.getName(), textState, new SoftButtonObject.OnEventListener() {

					/**
					 * ボタンが押された場合の処理
					 * @param softButtonObject
					 * @param onButtonPress
					 */
					@Override
					public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {

						//画像を変更する
						sdlManager.getScreenManager().beginTransaction();

						SdlArtwork artwork = null;
						if(sdlManager.getScreenManager().getPrimaryGraphic().getResourceId() ==  R.drawable.artwork01) {
							artwork = new SdlArtwork("artwork02.png", FileType.GRAPHIC_PNG, R.drawable.artwork02, true);
						}
						else{
							artwork = new SdlArtwork("artwork01.png", FileType.GRAPHIC_PNG, R.drawable.artwork01, true);
						}
						sdlManager.getScreenManager().setPrimaryGraphic(artwork);

						sdlManager.getScreenManager().commit(new CompletionListener() {
							@Override
							public void onComplete(boolean success) {
								Log.i(TAG, "ScreenManager update complete: " + success);
							}
						});
					}

					@Override
					public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

					}
				});

				//二つ目のボタン（アイコンだけのボタンを設定する）
				SdlArtwork artwrokIcon = new SdlArtwork("ic_speaker.png", FileType.GRAPHIC_PNG, R.drawable.ic_speaker, true);
				SoftButtonState iconState = new SoftButtonState("icon_button", null, artwrokIcon);
				//ボタンを作成する
				SoftButtonObject iconButtonObject = new SoftButtonObject(iconState.getName(), iconState, new SoftButtonObject.OnEventListener() {
					/**
					 * ボタンが押された場合の処理
					 * @param softButtonObject
					 * @param onButtonPress
					 */
					@Override
					public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
						speak("Hello S D L Android");
					}

					@Override
					public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {
					}
				});

				//ボタンを登録する
				List<SoftButtonObject> buttons = Arrays.asList(textButtonObject, iconButtonObject);
				sdlManager.getScreenManager().setSoftButtonObjects(buttons);

			}

			/**
			 * テンプレート設定のエラー時
			 * @param correlationId
			 * @param resultCode
			 * @param info
			 */
			@Override
			public void onError(int correlationId, Result resultCode, String info) {
				super.onError(correlationId, resultCode, info);
			}
		});

		//テンプレートを登録する
		sdlManager.sendRPC(setDisplayLayout);
	}

	/**
	 * 車輌データを登録します。
	 */
	private void registVehicleData(){
		GetVehicleData vdRequest = new GetVehicleData();

		//データを取得したい項目を設定します。
		vdRequest.setSpeed(true);	//速度

		vdRequest.setPrndl(true);   //シフトレバー

		vdRequest.setHeadLampStatus(true);   //ヘッドランプステータス
		//データの取得を行います。
		vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {

				Log.i(TAG, "GetVehicleData update complete: " + response.getSuccess());

				if(response.getSuccess()) {
					GetVehicleDataResponse getVehicleData = (GetVehicleDataResponse) response;

					sdlManager.getScreenManager().beginTransaction();
					String speed = String.format("%.02f km/h", getVehicleData.getSpeed());
					sdlManager.getScreenManager().setTextField3(speed);


					Log.d(MainActivity.LOG,"はいびーむ初期値"+getVehicleData.getHeadLampStatus().getHighBeamsOn());

					sdlManager.getScreenManager().setTextField4(
							getVehicleData.getPrndl().name()
					);

					//画面の設定を完了する
					sdlManager.getScreenManager().commit(new CompletionListener() {
						@Override
						public void onComplete(boolean success) {
							Log.i(TAG, "ScreenManager update complete: " + success);
						}
					});

					//以降は車輌データが変わった時にデータを受け取るようにする
					registSubscribeVehicleData();
				}

			}
		});
		sdlManager.sendRPC(vdRequest);

	}

	/**
	 * 車上情報が更新されたデータを受け取れるようにします。
	 */
	private void registSubscribeVehicleData(){

		//定期受信用のデータを設定する
		SubscribeVehicleData subscribeRequest = new SubscribeVehicleData();

		//定期的にデータを取得したい項目を設定します。
		subscribeRequest.setSpeed(true);		//エンジン回転数

		subscribeRequest.setPrndl(true);        //ソフトレバー情報

		subscribeRequest.setHeadLampStatus(true); //ッヘッドランプ情報

		//定期受信の登録を行います
		subscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				Log.i(TAG, "SubscribeVehicleData update complete: " + response.getSuccess());
			}
		});
		sdlManager.sendRPC(subscribeRequest);
	}

	/**
	 * アラートを表示する
	 * @param message
	 */
	private void alert(String message){
		Alert alert = new Alert();
		alert.setAlertText1(message);
		alert.setDuration(5000);
		sdlManager.sendRPC(alert);
	}

	/**
	 *  メッセージを再生する（英語のみ対応）
	 */
	private void speak(String message){
		if(message == null || message.length() == 0){
			return;
		}
		sdlManager.sendRPC(new Speak(TTSChunkFactory.createSimpleTTSChunks(message)));
	}
}
