package com.ionic.deploy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

class JsonHttpResponse {
  String message;
  Boolean error;
  JSONObject json;
}


public class IonicDeploy extends CordovaPlugin {
  String server = "http://www.exexm.com:8006";
  Context myContext = null;
  String app_id = null;
  String app_version = null;
  boolean debug = true;
  SharedPreferences prefs = null;
  CordovaWebView v = null;
  String version_label = null;
  boolean ignore_deploy = false;
  JSONObject last_update;
  boolean firstCope = false;

  public static final String NO_DEPLOY_LABEL = "NO_DEPLOY_LABEL";
  public static final String NO_DEPLOY_AVAILABLE = "NO_DEPLOY_AVAILABLE";
  public static final String NOTHING_TO_IGNORE = "NOTHING_TO_IGNORE";
  public static final int VERSION_AHEAD = 1;
  public static final int VERSION_MATCH = 0;
  public static final int VERSION_BEHIND = -1;

  /**
   * Sets the context of the Command. This can then be used to do things like
   * get file paths associated with the Activity.
   *
   * @param cordova The context of the main Activity.
   * @param webView The CordovaWebView Cordova is running in.
   */
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    this.myContext = this.cordova.getActivity().getApplicationContext();
    this.prefs = getPreferences();
    this.v = webView;
//    this.version_label = prefs.getString("ionicdeploy_version_label", IonicDeploy.NO_DEPLOY_LABEL);
//    this.initVersionChecks();
  //    copyCommon();
  }

  private String getUUID() {
    return this.prefs.getString("uuid", IonicDeploy.NO_DEPLOY_AVAILABLE);
  }

  private String getUUID(String defaultUUID) {
    return this.prefs.getString("uuid", defaultUUID);
  }

  private PackageInfo getAppPackageInfo() throws NameNotFoundException {
    PackageManager packageManager = this.cordova.getActivity().getPackageManager();
    PackageInfo packageInfo = packageManager.getPackageInfo(this.cordova.getActivity().getPackageName(), 0);
    return packageInfo;
  }

  private void initVersionChecks() {
    String ionicdeploy_version_label = IonicDeploy.NO_DEPLOY_LABEL;
    String uuid = this.getUUID();

    try {
      ionicdeploy_version_label = this.constructVersionLabel(this.getAppPackageInfo(), uuid);
    } catch (NameNotFoundException e) {
      logMessage("INIT", "Could not get package info");
    }

    if(!ionicdeploy_version_label.equals(IonicDeploy.NO_DEPLOY_LABEL)) {
      if(this.debug) {
        logMessage("INIT", "Version Label 1: " + this.version_label);
        logMessage("INIT", "Version Label 2: " + ionicdeploy_version_label);
      }
      if(!this.version_label.equals(ionicdeploy_version_label)) {
        this.ignore_deploy = true;
        this.updateVersionLabel(uuid);
        this.prefs.edit().remove("uuid").apply();
      }
    }
  }

  private String constructVersionLabel(PackageInfo packageInfo, String uuid) {
    String version = packageInfo.versionName;
    String timestamp = String.valueOf(packageInfo.lastUpdateTime);
    return version + ":" + timestamp + ":" + uuid;
  }

  private String[] deconstructVersionLabel(String label) {
    return label.split(":");
  }

  public Object onMessage(String id, Object data) {
//    boolean is_nothing = "file:///".equals(String.valueOf(data));
//    boolean is_index = "file:///android_asset/www/index.html".equals(String.valueOf(data));
//    boolean is_original = (is_nothing || is_index) ? true : false;
//    logMessage("LOAD", "id :" + id);
//    logMessage("LOAD", "data :" + String.valueOf(data));
//    if("onPageStarted".equals(id) && is_original) {
//      logMessage("LOAD", "Init Deploy Version enter");
//      final String uuid = this.getUUID();
//        logMessage("LOAD", "Init Deploy Version enter "+ uuid);
//      if(!IonicDeploy.NO_DEPLOY_AVAILABLE.equals(uuid)) {
//        logMessage("LOAD", "Init Deploy Version " + uuid);
//        this.redirect(uuid, false);
//      }
//    }
    return null;
  }


  public void copyCommon(){
    String uuid = getUUID();
    if(NO_DEPLOY_AVAILABLE.equals(uuid)) {

      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
         String uuid  = "common";

          if (!checkDir(uuid)) {
              Log.e("copyCommon","copy begin");
              File versionDir = myContext.getDir(uuid, Context.MODE_PRIVATE);
              copyFilesFassets(myContext, "www", versionDir.getAbsolutePath());
//              prefs.getString("uuid", uuid);
              prefs.edit().putString("uuid", uuid).apply();
              prefs.edit().putString("loaded_uuid", uuid).apply();
              Log.e("copyCommon", "copy end");
          }
        }
      });
    }
  }

  /**
   * Executes the request and returns PluginResult.
   *
   * @param action            The action to execute.
   * @param args              JSONArry of arguments for the plugin.
   * @param callbackContext   The callback id used when calling back into JavaScript.
   * @return                  True if the action was valid, false if not.
   */
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {

    this.app_id = args.getString(0);
    this.prefs = getPreferences();

    initApp(args.getString(0));

    final SharedPreferences prefs = this.prefs;

    if (action.equals("initialize")) {
      this.server = args.getString(1);
      return true;
    } else if (action.equals("check")) {
      logMessage("CHECK", "Checking for updates");
      final String uuid = args.getString(0);
//      final String loaded_uuid  = this.prefs.getString("loaded_uuid", "");

      if (uuid != null && uuid.length()>0){
        prefs.edit().putString("trans_version", uuid).apply();
      }
      final String channel_tag = args.getString(1);
      boolean disable = false;
      if (args.length()>2){
        disable = args.getBoolean(2);
      }
      final boolean include_disable = disable;
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          checkForUpdates(callbackContext, channel_tag, include_disable);
        }
      });
      return true;
    } else if (action.equals("download")) {
      logMessage("DOWNLOAD", "Downloading updates");
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          downloadUpdate(callbackContext);
        }
      });
      return true;
    } else if (action.equals("extract")) {
      logMessage("EXTRACT", "Extracting update");
//      final String uuid = this.getUUID("");
//         SharedPreferences prefs = getPreferences();

//     Set the saved uuid to the most recently acquired upstream_uuid
      final  String uuid = prefs.getString("upstream_uuid", "");
      final boolean firstCoped = this.prefs.getBoolean("firstCoped", false);
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
//          if (uuid.equals("common") && !firstCoped)
           if(!checkDir(uuid)){
             copyLastVersion(uuid);
           }

          unzip("www.zip", uuid, callbackContext);
        }
      });
      return true;
    } else if (action.equals("redirect")) {
      String uuid = args.getString(0);
      if (uuid == null || uuid.length() == 0)
        uuid = this.getUUID("");
      this.redirect(uuid, true);
      return true;
    } else if (action.equals("info")) {
      String uuid = args.getString(0);
      this.info(uuid, callbackContext);
      return true;
    } else if (action.equals("getVersions")) {
      callbackContext.success(this.getDeployVersions());
      return true;
    } else if (action.equals("deleteVersion")) {
      final String uuid = args.getString(1);
      boolean status = this.removeVersion(uuid);
      if (status) {
        callbackContext.success();
      } else {
        callbackContext.error("Error attempting to remove the version, are you sure it exists?");
      }
      return true;
    } else if (action.equals("getMetadata")) {
      String uuid = null;
      try {
        uuid = args.getString(1);
      } catch (JSONException e) {
        uuid = this.prefs.getString("upstream_uuid", "");
      }

      if (uuid.equals("null")) {
        uuid = this.prefs.getString("upstream_uuid", "");
      }

      if(uuid == null || uuid.equals("")) {
        callbackContext.error("NO_DEPLOY_UUID_AVAILABLE");
      } else {
        final String metadata_uuid = uuid;
        this.getMetadata(callbackContext, metadata_uuid);
      }
      return true;
    } else {
      return false;
    }
  }

  private JSONObject getMetadata(CallbackContext callbackContext, final String uuid) {
    String endpoint = "/api/v1/apps/" + this.app_id + "/updates/" + uuid + "/";
    JsonHttpResponse response = new JsonHttpResponse();
    JSONObject json = new JSONObject();
    HttpURLConnection urlConnection = null;

    String result = "{}";
    try {
      URL url = new URL(this.server + endpoint);
      urlConnection = (HttpURLConnection) url.openConnection();
      InputStream in = new BufferedInputStream(urlConnection.getInputStream());
      result = readStream(in);
    } catch (MalformedURLException e) {
      callbackContext.error("DEPLOY_HTTP_ERROR");
      response.error = true;
    } catch (IOException e) {
      callbackContext.error("DEPLOY_HTTP_ERROR");
      response.error = true;
    }

    if (urlConnection != null) {
      urlConnection.disconnect();
    }

    JSONObject jsonResponse = null;
    try {
      jsonResponse = new JSONObject(result);
      callbackContext.success(jsonResponse);
    } catch (JSONException e) {
      response.error = true;
      callbackContext.error("There was an error fetching the metadata");
    }
    return jsonResponse;
  }


  public  String getString(InputStream inputStream) {
    InputStreamReader inputStreamReader = null;
    try {
      inputStreamReader = new InputStreamReader(inputStream, "gbk");
    } catch (UnsupportedEncodingException e1) {
      e1.printStackTrace();
    }
    BufferedReader reader = new BufferedReader(inputStreamReader);
    StringBuffer sb = new StringBuffer("");
    String line;
    try {
      while ((line = reader.readLine()) != null) {
        sb.append(line);
        sb.append("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return sb.toString();
  }



  private  String getHtmlVersion(String uuid){
    try {
//      String real_path = "file:///data/data/com.xmexe.exe/app_"+uuid;
//      File file =  new File(real_path);
      File file =  this.myContext.getDir(uuid, Context.MODE_PRIVATE);
      if (file.exists()){
        String path = file.getAbsolutePath();
        path += "/js/versionConfig.txt";
        logMessage("getHtmlVersion", path);
        File version = new File(path);
        if (version.exists()){
          FileInputStream fis = new FileInputStream(version);
          String htmlv = getString(fis);
          return htmlv;
        }else {
          return  "";
        }
      }else {
        return "";
      }

    } catch (Exception e) {
    }
    return  "";
  }

  private  boolean checkDir(String uuid){
    try {
      File file =  this.myContext.getDir(uuid, Context.MODE_PRIVATE);
//      String real_path = "file:///data/data/com.xmexe.exe/app_"+uuid;
//      File file =  new File(real_path);
      if (file.exists()){
        String path = file.getAbsolutePath();
        path += "/js/versionConfig.txt";
//        logMessage("INFO", path);
        File version = new File(path);
        if (version.exists()){
          FileInputStream fis = new FileInputStream(version);
          String htmlv = getString(fis);
          return true;
        }else {
          return  false;
        }
      }else {
        return false;
      }

    } catch (Exception e) {
    }
    return  false;
  }

  private void info(String uuid, CallbackContext callbackContext) {
    JSONObject json = new JSONObject();

    try {
//      String real_path = "file:///data/data/com.xmexe.exe/app_"+uuid;
//      File file =  new File(real_path);
      File file =  this.myContext.getDir(uuid, Context.MODE_PRIVATE);
//      this.myContext.getDir()
      if (file.exists()){

        String path = file.getAbsolutePath();
        path += "/js/versionConfig.txt";
        logMessage("INFO", path);
        //
        File version = new File(path);

        if (version.exists()){
          FileInputStream fis = new FileInputStream(version);
          String htmlv = getString(fis);
          json.put("exists", true);
          json.put("htmlversion", htmlv);
          logMessage("INFO", "exists " + true);
          logMessage("INFO", "htmlversion" + htmlv);
        }else {
          json.put("exists", false);
        }

      }else {
        json.put("exists", false);
      }

    } catch (Exception e) {
      callbackContext.error("Unable to gather deploy info: " + e.toString());
    }finally {

    }
    callbackContext.success(json);

  }

  private void initApp(String app_id) {
    this.app_id = app_id;
    SharedPreferences prefs = this.prefs;

    prefs.edit().putString("app_id", this.app_id).apply();
    // Used for keeping track of the order versions were downloaded
    int version_count = prefs.getInt("version_count", 0);
    prefs.edit().putInt("version_count", version_count).apply();
  }

  private void checkForUpdates(CallbackContext callbackContext, final String channel_tag, final boolean include_disable) {

    this.last_update = null;
    String ignore_version = this.prefs.getString("ionicdeploy_version_ignore", "");
    String deployed_version = this.prefs.getString("uuid", "");
    String trans_version = this.prefs.getString("trans_version", "");
    String loaded_version = this.prefs.getString("loaded_uuid", "");
//    boolean include_disable = false;
    JsonHttpResponse response = postDeviceDetails(trans_version , channel_tag, include_disable);
    Boolean updatesAvailable =true;
    Boolean result = false;
    if (response.json != null) {
      try {
        this.last_update = response.json.getJSONObject("data");
        result = Boolean.valueOf(response.json.getString("success"));
        String update_uuid = last_update.getString("html_new_version");
        Boolean common = last_update.getBoolean("has_own_sp");

        if (!common || trans_version.length() == 0){
          trans_version = "common";
        }

//              if(!update_uuid.equals(ignore_version) && !update_uuid.equals(loaded_version)) {
        prefs.edit().putString("upstream_uuid", trans_version).apply();
//              }else{
//                  updatesAvailable = new  Boolean(false);
//              }

        if(updatesAvailable && result) {
//                  callbackContext.success("true");
          callbackContext.success(last_update);
        } else {
          callbackContext.success("false");
        }
      }catch(JSONException e){
        callbackContext.success("false");
      }
//
//
//        callbackContext.success("true");
    } else {
      logMessage("CHECK", "Unable to check for updates.");
      callbackContext.success("false");
    }


//        Boolean result = Boolean.valueOf(response.json.getString("success"));
//        Boolean updatesAvailable = Boolean.valueOf(response.json.getString("update_available"));

//        if(!result) {
//          logMessage("CHECK", "Refusing update due to incompatible binary version");
//          callbackContext.success("false");
//        }else {
//          try {
//            JSONObject update = response.json.getJSONObject("data");
//            String update_uuid = update.getString("new_version");
//            if(!update_uuid.equals(ignore_version) && !update_uuid.equals(loaded_version)) {
//              prefs.edit().putString("upstream_uuid", update_uuid).apply();

//            } else {
////              updatesAvailable = new Boolean(false);
//            }

//          } catch (JSONException e) {
//            callbackContext.error("Update information is not available");
//          }
//        }

//        if(updatesAvailable && compatible) {
//          callbackContext.success("true");
//        } else {
//          callbackContext.success("false");
//        }

//    } catch (JSONException e) {
//      logMessage("CHECK", e.toString());
//      callbackContext.error("Error checking for updates.");
//    }
  }

  private void downloadUpdate(CallbackContext callbackContext) {
      String upstream_uuid = this.prefs.getString("upstream_uuid", "");
//    if (upstream_uuid != "" && this.hasVersion(upstream_uuid)) {
//      // Set the current version to the upstream uuid
//      prefs.edit().putString("uuid", upstream_uuid).apply();
//      callbackContext.success("false");
//    } else {
    try {
      String url = this.last_update.getString("html_packet_url");
      logMessage("DOWNLOAD", url);
      final DownloadTask downloadTask = new DownloadTask(this.myContext, callbackContext);
      downloadTask.execute(url);
    } catch (JSONException e) {
      logMessage("DOWNLOAD", e.toString());
      callbackContext.error("Error fetching download");
    }
//    }
  }

  /**
   * Get a list of versions that have been downloaded
   *
   * @return
   */
  private Set<String> getMyVersions() {
    SharedPreferences prefs = this.prefs;
    return prefs.getStringSet("my_versions", new HashSet<String>());
  }

  private JSONArray getDeployVersions() {
    Set<String> versions = this.getMyVersions();
    JSONArray deployVersions = new JSONArray();
    for (String version : versions) {
      String[] version_string = version.split("\\|");
      deployVersions.put(version_string[0]);
    }
    return deployVersions;
  }

  /**
   * Check to see if we already have the version to be downloaded
   *
   * @param uuid
   * @return
   */
  private boolean hasVersion(String uuid) {
    Set<String> versions = this.getMyVersions();

    logMessage("HASVER", "Checking " + uuid + "...");
    for (String version : versions) {
      String[] version_string = version.split("\\|");
      logMessage("HASVER", version_string[0] + " == " + uuid);
      if (version_string[0].equals(uuid)) {
        logMessage("HASVER", "Yes");
        return true;
      }
    }

    logMessage("HASVER", "No");
    return false;
  }

  /**
   * Save a new version string to our list of versions
   *
   * @param uuid
   */
  private void saveVersion(String uuid) {
    SharedPreferences prefs = this.prefs;

    Integer version_count = prefs.getInt("version_count", 0) + 1;
    prefs.edit().putInt("version_count", version_count).apply();

    uuid = uuid + "|" + version_count.toString();
    Set<String> versions = this.getMyVersions();
    versions.add(uuid);
    prefs.edit().putStringSet("my_versions", versions).apply();
    this.cleanupVersions();
  }

  private void cleanupVersions() {
    // Let's keep 5 versions around for now
    SharedPreferences prefs = this.prefs;

    int version_count = prefs.getInt("version_count", 0);
    Set<String> versions = this.getMyVersions();

    if (version_count > 3) {
      int threshold = version_count - 3;

      for (Iterator<String> i = versions.iterator(); i.hasNext();) {
        String version = i.next();
        String[] version_string = version.split("\\|");
        logMessage("VERSION", version);
        int version_number = Integer.parseInt(version_string[1]);
        if (version_number < threshold) {
          logMessage("REMOVING", version);
          i.remove();
          removeVersion(version_string[0]);
        }
      }

      Integer version_c = versions.size();
      logMessage("VERSIONCOUNT", version_c.toString());
      prefs.edit().putStringSet("my_versions", versions).apply();
    }
  }

  private void removeVersionFromPreferences(String uuid) {
    SharedPreferences prefs = this.prefs;
    Set<String> versions = this.getMyVersions();
    Set<String> newVersions = new HashSet<String>();

    for (String version : versions) {
      String[] version_string = version.split("\\|");
      String tempUUID = version_string[0];
      if (!tempUUID.equals(uuid)) {
        newVersions.add(version);
      }
      prefs.edit().putStringSet("my_versions", newVersions).apply();
    }
  }


  /**
   * Remove a deploy version from the device
   *
   * @param uuid
   * @return boolean Success or failure
   */
  private boolean removeVersion(String uuid) {
    if (uuid.equals(this.getUUID())) {
      SharedPreferences prefs = this.prefs;
      prefs.edit().putString("uuid", "").apply();
      prefs.edit().putString("loaded_uuid", "").apply();
    }
      File versionDir = this.myContext.getDir(uuid, Context.MODE_PRIVATE);
    if (versionDir.exists()) {
      String deleteCmd = "rm -r " + versionDir.getAbsolutePath();
      Runtime runtime = Runtime.getRuntime();
      try {
        runtime.exec(deleteCmd);
        removeVersionFromPreferences(uuid);
        return true;
      } catch (IOException e) {
        logMessage("REMOVE", "Failed to remove " + uuid + ". Error: " + e.getMessage());
      }
    }
    return false;
  }

  private JsonHttpResponse postDeviceDetails(String uuid, final String channel_tag, boolean include_disable) {

//    String endpoint = "/api/v1/apps/" + this.app_id + "/updates/check/";
    String endpoint = "/Version/Upgrade";
    JsonHttpResponse response = new JsonHttpResponse();
    JSONObject json = new JSONObject();

    try {
      try {
        PackageInfo info =  getAppPackageInfo();
        app_id = info.packageName;
        app_version = info.versionName;
      } catch (NameNotFoundException e) {
        e.printStackTrace();
      }

      logMessage("postDeviceDetails", "uuid " + uuid);
      logMessage("postDeviceDetails", "channel_tag " + channel_tag);
      json.put("platform", "0");
      json.put("app_id", app_id);
      json.put("system_current_version", app_version);
      json.put("html_current_version", channel_tag);
      json.put("tenant_id", uuid);
      json.put("include_disable", include_disable);

      Boolean b = checkDir(uuid);
      logMessage("version", "log b " + b);
      json.put("has_own_sp", b);

//      String htmlv = getHtmlVersion(uuid);
//      if(htmlv.length() != 0){
//        logMessage("check ", "html :" + htmlv);
//        json.put("html_current_version", htmlv);
//      }else{
//
//        String commonv = getHtmlVersion("common");
//        logMessage("check ", "commonv :" + htmlv);
//        if(commonv.length() != 0) {
//          json.put("html_current_version", commonv);
//        }
//      }


//      String html = ge

//      json.put("platform", "0");
//      json.put("app_id", app_id);
//      json.put("system_current_version", app_version);
//      json.put("html_current_version",uuid);


//      json.put("device_app_version", this.deconstructVersionLabel(this.version_label)[0]);
//      json.put("device_deploy_uuid", uuid);
//      json.put("device_platform", "android");
//      json.put("channel_tag", channel_tag);

      String params = json.toString();
      byte[] postData = params.getBytes("UTF-8");
      int postDataLength = postData.length;

      URL url = new URL(this.server + endpoint);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();

      conn.setDoOutput(true);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("Charset", "utf-8");
      conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));

      DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
      wr.write( postData );

      InputStream in = new BufferedInputStream(conn.getInputStream());
      String result = readStream(in);

      JSONObject jsonResponse = new JSONObject(result);

      response.json = jsonResponse;
    } catch (JSONException e) {
      response.error = true;
    } catch (MalformedURLException e) {
      response.error = true;
    } catch (IOException e) {
      response.error = true;
    }

    return response;
  }

  private SharedPreferences getPreferences() {
    // Request shared preferences for this app id
    SharedPreferences prefs = this.myContext.getSharedPreferences("com.xmexe.exe.preferences", Context.MODE_PRIVATE);
    return prefs;
  }

  private String readStream(InputStream is) {
    try {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      int i = is.read();
      while(i != -1) {
        bo.write(i);
        i = is.read();
      }
      return bo.toString();
    } catch (IOException e) {
      return "";
    }
  }

  private void logMessage(String tag, String message) {
    if (this.debug == true) {
      Log.i("IONIC.DEPLOY." + tag, message);
    }
  }

  private void updateVersionLabel(String ignore_version) {
    try {
      String ionicdeploy_version_label = this.constructVersionLabel(this.getAppPackageInfo(), this.getUUID());
      this.prefs.edit().putString("ionicdeploy_version_label", ionicdeploy_version_label).apply();
      this.version_label = prefs.getString("ionicdeploy_version_label", IonicDeploy.NO_DEPLOY_LABEL);
      this.prefs.edit().putString("ionicdeploy_version_ignore", ignore_version).apply();
    } catch (NameNotFoundException e) {
      logMessage("LABEL", "Could not get package info");
    }
  }

  /**
   * Extract the downloaded archive
   *
   * @param zip
   * @param location
   */
  private void unzip(String zip, String location, CallbackContext callbackContext) {
    SharedPreferences prefs = getPreferences();
    String upstream_uuid = prefs.getString("upstream_uuid", "");

    logMessage("UNZIP", upstream_uuid);

    this.ignore_deploy = false;
    this.updateVersionLabel(IonicDeploy.NOTHING_TO_IGNORE);

//    if (upstream_uuid != "" && this.hasVersion(upstream_uuid)) {
//      callbackContext.success("done"); // we have already extracted this version
//      return;
//    }

    try  {
      FileInputStream inputStream = this.myContext.openFileInput(zip);
      ZipInputStream zipInputStream = new ZipInputStream(inputStream);
      ZipEntry zipEntry = null;

      // Get the full path to the internal storage
      String filesDir = this.myContext.getFilesDir().toString();

      // Make the version directory in internal storage
      File versionDir = this.myContext.getDir(location, Context.MODE_PRIVATE);

      logMessage("UNZIP_DIR", versionDir.getAbsolutePath().toString());

      // Figure out how many entries are in the zip so we can calculate extraction progress
      ZipFile zipFile = new ZipFile(this.myContext.getFileStreamPath(zip).getAbsolutePath().toString());
      float entries = new Float(zipFile.size());

      logMessage("ENTRIES", "Total: " + (int) entries);

      float extracted = 0.0f;

      while ((zipEntry = zipInputStream.getNextEntry()) != null) {
        File newFile = new File(versionDir + "/" + zipEntry.getName());
        newFile.getParentFile().mkdirs();

        byte[] buffer = new byte[2048];

        FileOutputStream fileOutputStream = new FileOutputStream(newFile);
        BufferedOutputStream outputBuffer = new BufferedOutputStream(fileOutputStream, buffer.length);
        int bits;
        while((bits = zipInputStream.read(buffer, 0, buffer.length)) != -1) {
          outputBuffer.write(buffer, 0, bits);
        }

        zipInputStream.closeEntry();
        outputBuffer.flush();
        outputBuffer.close();

        extracted += 1;

        float progress = (extracted / entries) * new Float("100.0f");
        logMessage("EXTRACT", "Progress: " + (int) progress + "%");

        PluginResult progressResult = new PluginResult(PluginResult.Status.OK, (int) progress);
        progressResult.setKeepCallback(true);
        callbackContext.sendPluginResult(progressResult);
      }
      zipInputStream.close();

    } catch(Exception e) {
      //TODO Handle problems..
      logMessage("UNZIP_STEP", "Exception: " + e.getMessage());
      callbackContext.success("false");
      return;
    }

    // Save the version we just downloaded as a version on hand
//    saveVersion(upstream_uuid);

    String wwwFile = this.myContext.getFileStreamPath(zip).getAbsolutePath().toString();
    if (this.myContext.getFileStreamPath(zip).exists()) {
      String deleteCmd = "rm -r " + wwwFile;
      Runtime runtime = Runtime.getRuntime();
      try {
        runtime.exec(deleteCmd);
        logMessage("REMOVE", "Removed www.zip");
      } catch (IOException e) {
        logMessage("REMOVE", "Failed to remove " + wwwFile + ". Error: " + e.getMessage());
      }
    }
//         SharedPreferences prefs = getPreferences();

//     Set the saved uuid to the most recently acquired upstream_uuid
//      String uuid = prefs.getString("upstream_uuid", "");

      prefs.edit().putString("uuid", upstream_uuid).apply();
      callbackContext.success("done");
  }

  //增加的copy 操作
  private void copyLastVersion(String location){
    File versionDir = this.myContext.getDir(location, Context.MODE_PRIVATE);
    String lastLoadUUid = prefs.getString("loaded_uuid", "");
    String from = "";
    String to = versionDir.getAbsolutePath()+"/";
    if (lastLoadUUid.length() > 0){
      File lastLoadFile = this.myContext.getDir(lastLoadUUid, Context.MODE_PRIVATE);

      from = lastLoadFile.getAbsolutePath();
      copyFolder(from, to);
//      String  CopeCmd = "cp -rf " + from + " " + to ;
//      Runtime runtime = Runtime.getRuntime();
//      try {
//        runtime.exec(CopeCmd);
//        logMessage("COPE", "cope " + from + " to "+ to);
//      } catch (IOException e) {
//        logMessage("COPE", "Failed to cope " + from + " to "+ to+". Error: " + e.getMessage());
//      }
    }else{
      CopyAssets("www", to);
 //       copyFilesFassets(myContext,"www", to);
//      File file = new File("file:///android_asset/www");
//      from = file.getAbsolutePath()+"/";
    }


    firstCope = true;

    prefs.edit().putBoolean("firstCoped", true).apply();
  }


    /**
     *  从assets目录中复制整个文件夹内容
     *  @param  context  Context 使用CopyFiles类的Activity
     *  @param  oldPath  String  原文件路径  如：/aa
     *  @param  newPath  String  复制后路径  如：xx:/bb/cc
     */
    public void copyFilesFassets(Context context,String oldPath,String newPath) {
        try {
            Log.e("copyFilesFassets","oldPath: "+ oldPath);
            String fileNames[] = context.getAssets().list(oldPath);//获取assets目录下的所有文件及目录名
            if (fileNames.length > 0) {//如果是目录
                File file = new File(newPath);
                file.mkdirs();//如果文件夹不存在，则递归
                for (String fileName : fileNames) {
                    copyFilesFassets(context,oldPath + "/" + fileName,newPath+"/"+fileName);
                }
            } else {//如果是文件
                InputStream is = context.getAssets().open(oldPath);
                FileOutputStream fos = new FileOutputStream(new File(newPath));
                byte[] buffer = new byte[1024];
                int byteCount=0;
                while((byteCount=is.read(buffer))!=-1) {//循环从输入流读取 buffer字节
                    fos.write(buffer, 0, byteCount);//将读取的输入流写入到输出流
                }
                fos.flush();//刷新缓冲区
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            //如果捕捉到错误则通知UI线程

        }
    }
//
  //  /**
//   * ��������ļ�������
//   * @param oldPath String ԭ�ļ�·�� �磺c:/fqf
//   * @param newPath String ���ƺ�·�� �磺f:/fqf/ff
//   * @return boolean
//   */
  public void copyFolder(String oldPath, String newPath) {

    try {
      (new File(newPath)).mkdirs(); //����ļ��в����� �������ļ���
      File a=new File(oldPath);
      String[] file=a.list();
      File temp=null;
      for (int i = 0; i < file.length; i++) {
        if(oldPath.endsWith(File.separator)){
          temp=new File(oldPath+file[i]);
        }
        else{
          temp=new File(oldPath+File.separator+file[i]);
        }

        if(temp.isFile()){
          FileInputStream input = new FileInputStream(temp);
          FileOutputStream output = new FileOutputStream(newPath + "/" +
                  (temp.getName()).toString());
          byte[] b = new byte[1024 * 5];
          int len;
          while ( (len = input.read(b)) != -1) {
            output.write(b, 0, len);
          }
          output.flush();
          output.close();
          input.close();
        }
        if(temp.isDirectory()){//
          copyFolder(oldPath+"/"+file[i],newPath+"/"+file[i]);
        }
      }
    }
    catch (Exception e) {
      System.out.println("");
      e.printStackTrace();
    }
  }

  private void CopyAssets(String assetDir,String dir) {
    String[] files;
    try {
      files = myContext.getAssets().list(assetDir);
    } catch (IOException e1) {
      return;
    }
    File mWorkingPath = new File(dir);
    //if this directory does not exists, make one.
    if (!mWorkingPath.exists()) {
      if (!mWorkingPath.mkdirs()) {
      }
    }

    for (int i = 0; i < files.length; i++) {
      try {
        String fileName = files[i];
        //we make sure file name not contains '.' to be a folder.
        if (!fileName.contains(".")) {
          if (0 == assetDir.length()) {
            CopyAssets(fileName, dir + fileName + "/");
          } else {
            CopyAssets(assetDir + "/" + fileName, dir + fileName + "/");
          }
          continue;
        }
        File outFile = new File(mWorkingPath, fileName);
        if (outFile.exists())
          outFile.delete();
        InputStream in = null;
        if (0 != assetDir.length())
          in = myContext.getAssets().open(assetDir + "/" + fileName);
        else
          in = myContext.getAssets().open(fileName);
        OutputStream out = new FileOutputStream(outFile);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
        }

        in.close();
        out.close();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }



//  private void copyAssets(String inFolder, String outFolder) {
//    AssetManager assetManager = myContext.getAssets();
//    String[] files = null;
//    try {
//      files = assetManager.list(inFolder);
//    } catch (IOException e) {
//      Log.e("tag", "Failed to get asset file list.", e);
//    }
//    for(String filename : files) {
//      InputStream in = null;
//      OutputStream out = null;
//      try {
//        in = assetManager.open(filename);
//
//        File outFile = new File(outFolder, filename);
//        out = new FileOutputStream(outFile);
//        copyFile(in, out);
//        in.close();
//        in = null;
//        out.flush();
//        out.close();
//        out = null;
//      } catch(IOException e) {
//        Log.e("tag", "Failed to copy asset file: " + filename, e);
//      }
//    }
//  }
//  private void copyFile(InputStream in, OutputStream out) throws IOException {
//    byte[] buffer = new byte[1024];
//    int read;
//    while((read = in.read(buffer)) != -1){
//      out.write(buffer, 0, read);
//    }
//  }


  private void redirect(final String uuid, final boolean recreatePlugins) {

    if (!uuid.equals("")) {
      prefs.edit().putString("uuid", uuid).apply();
      final File versionDir = this.myContext.getDir(uuid, Context.MODE_PRIVATE);
      final String deploy_url = versionDir.toURI() + "index.html";

      cordova.getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
            logMessage("LOAD", "Init loaded_uuid " + uuid);
          prefs.edit().putString("loaded_uuid", uuid).apply();
          webView.loadUrlIntoView(deploy_url, recreatePlugins);
          //从新刷新UI
//          CordovaActivity cActivity = (CordovaActivity) cordova.getActivity();
//          cActivity.loadUrl(deploy_url);
        }
      });
    }
  }

  private class DownloadTask extends AsyncTask<String, Integer, String> {
    private Context myContext;
    private CallbackContext callbackContext;

    public DownloadTask(Context context, CallbackContext callbackContext) {
      this.myContext = context;
      this.callbackContext = callbackContext;
    }

    @Override
    protected String doInBackground(String... sUrl) {
      InputStream input = null;
      FileOutputStream output = null;
      HttpURLConnection connection = null;
      try {
        URL url = new URL(sUrl[0]);
        connection = (HttpURLConnection) url.openConnection();
        connection.connect();

        // expect HTTP 200 OK, so we don't mistakenly save error report
        // instead of the file
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
          callbackContext.error("更新包下载失败");
          return "Server returned HTTP " + connection.getResponseCode()
                  + " " + connection.getResponseMessage();
        }

        // this will be useful to display download percentage
        // might be -1: server did not report the length
        float fileLength = new Float(connection.getContentLength());

        logMessage("DOWNLOAD", "File size: " + fileLength);

        // download the file
        input = connection.getInputStream();
        output = this.myContext.openFileOutput("www.zip", Context.MODE_PRIVATE);

        byte data[] = new byte[4096];
        float total = 0;
        int count;
        while ((count = input.read(data)) != -1) {
          total += count;

          output.write(data, 0, count);

          // Send the current download progress to a callback
          if (fileLength > 0) {
            float progress = (total / fileLength) * new Float("100.0f");
            logMessage("DOWNLOAD", "Progress: " + (int) progress + "%");
            PluginResult progressResult = new PluginResult(PluginResult.Status.OK, (int) progress);
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);
          }
        }
      } catch (Exception e) {
        callbackContext.error("更新包下载失败...");
        return e.toString();
      } finally {
        try {
          if (output != null)
            output.close();
          if (input != null)
            input.close();
        } catch (IOException ignored) {

        }

        if (connection != null)
          connection.disconnect();
      }

      // Request shared preferences for this app id
 //     SharedPreferences prefs = getPreferences();

      // Set the saved uuid to the most recently acquired upstream_uuid
//      String uuid = prefs.getString("upstream_uuid", "");
//
//      prefs.edit().putString("uuid", uuid).apply();

      callbackContext.success("true");
      return null;
    }
  }
}
